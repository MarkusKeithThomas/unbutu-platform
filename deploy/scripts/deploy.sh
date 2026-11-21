#!/usr/bin/env bash
# Deploy môi trường prod trên server unbutu (ADR-0003, ADR-0004).
#
#   sudo /opt/unbutu-platform/bin/deploy.sh <images.env>     # admin chạy tay
#   sudo /opt/unbutu-platform/bin/deploy.sh - < images.env   # CI: ssh deploy@unbutu (forced command, sudoers chỉ cho đúng lệnh này)
#
# <images.env> do release.yml tạo: <SVC>_IMAGE=ghcr.io/...@sha256:... (theo DIGEST) + APP_VERSION + GIT_SHA.
#
# Các bước:
#   1. Kiểm tra images.env (chỉ chấp nhận image GHCR của mình, theo digest).
#   2. Sinh env/<service>.env từ AWS: SSM /unbutu/prod/<service>/env-map (đường dẫn) + Secrets Manager (value).
#      Thiếu env-map / secret / key → dừng, KHÔNG deploy.
#   3. Tải compose.app.yml + postgres/init đúng commit GIT_SHA từ GitHub (repo public) → file deploy luôn khớp image.
#   4. docker compose pull + up --wait (chờ healthcheck) + smoke test qua gateway.
#   5. Lỗi → rollback về images (và file deploy) của lần deploy thành công trước đó.
#
# Quyền: chạy bằng root (đọc /root/.aws của IAM user unbutu-server – chỉ đọc). Value secret không bao giờ in ra log.
# Bản thân script này KHÔNG được CI cập nhật – admin cài tay (CI không có đường sửa code chạy bằng root).
set -euo pipefail

readonly ENVIRONMENT=prod
readonly APP_DIR=/opt/unbutu-platform/prod
readonly PROJECT=prod
readonly INFRA_SERVICES=(postgres)                   # service hạ tầng (không có trong images.env) nhưng cần env-map
readonly IMAGE_RE='^[A-Z][A-Z0-9_]*_IMAGE=ghcr\.io/markuskeiththomas/[a-z0-9-]+@sha256:[0-9a-f]{64}$'
readonly VERSION_RE='^APP_VERSION=[0-9a-f]{7,40}$'
readonly GIT_SHA_RE='^GIT_SHA=[0-9a-f]{40}$'
readonly REPO_RAW=https://raw.githubusercontent.com/MarkusKeithThomas/unbutu-platform
readonly DEPLOY_FILES=(compose.app.yml postgres/init/01-create-service-databases.sh)
readonly SMOKE_URL=http://127.0.0.1:8080/api/catalog/ping
export AWS_REGION=ap-southeast-1

log() { printf '%s [deploy] %s\n' "$(date -u +%FT%TZ)" "$*" >&2; }
die() { log "LỖI: $*"; exit 1; }

compose() {
    local images_env=$1; shift
    docker compose -p "$PROJECT" --project-directory "$APP_DIR" -f "$APP_DIR/compose.app.yml" \
        --env-file "$images_env" "$@"
}

# ---------- 1. images.env ----------
validate_images_env() {
    local file=$1 line
    grep -qE "$VERSION_RE" "$file" || die "$file thiếu APP_VERSION"
    while IFS= read -r line || [[ -n $line ]]; do
        [[ -z $line ]] && continue
        [[ $line =~ $IMAGE_RE || $line =~ $VERSION_RE || $line =~ $GIT_SHA_RE ]] \
            || die "$file có dòng không hợp lệ: $line"
    done <"$file"
}

# CATALOG_SERVICE_IMAGE=... → catalog-service
app_services() {
    sed -nE 's/^([A-Z0-9_]+)_IMAGE=.*/\1/p' "$1" | tr 'A-Z_' 'a-z-'
}

app_version() {
    sed -nE 's/^APP_VERSION=//p' "$1"
}

# ---------- 2. env/<service>.env từ AWS ----------
declare -A SECRET_CACHE=()   # mỗi secret chỉ gọi AWS 1 lần / lần deploy
TMP_FILE=""
trap '[[ -n $TMP_FILE ]] && rm -f "$TMP_FILE"' EXIT

load_secret() {   # không gọi trong $(...) – để cache và die() có hiệu lực ở shell chính
    local id=$1
    [[ -n ${SECRET_CACHE[$id]+x} ]] && return 0
    SECRET_CACHE[$id]=$(aws secretsmanager get-secret-value --secret-id "$id" \
        --query SecretString --output text) || die "không đọc được secret $id"
}

render_env() {
    local svc=$1 map name value secret key
    map=$(aws ssm get-parameter --name "/unbutu/$ENVIRONMENT/$svc/env-map" \
        --query Parameter.Value --output text) || die "không đọc được env-map của $svc"
    jq -e '(.env | type == "object") and (.secrets | type == "object")
           and all(.env[]; type == "string" and (test("\n") | not))' <<<"$map" >/dev/null \
        || die "$svc: env-map sai cấu trúc"

    TMP_FILE=$(mktemp "$APP_DIR/env/.$svc.XXXXXX")
    jq -r '.env | to_entries[] | "\(.key)=\(.value)"' <<<"$map" >"$TMP_FILE"

    while IFS=$'\t' read -r name secret key; do
        load_secret "$secret"
        value=$(jq -er --arg k "$key" '.[$k] | strings' <<<"${SECRET_CACHE[$secret]}") \
            || die "$svc: secret $secret thiếu key $key (biến $name)"
        [[ -n $value && $value != *$'\n'* ]] || die "$svc: value của $name rỗng hoặc nhiều dòng"
        printf '%s=%s\n' "$name" "$value" >>"$TMP_FILE"
    done < <(jq -r '.secrets | to_entries[] | [.key, .value.secret, .value.key] | @tsv' <<<"$map")

    grep -vqE '^[A-Z_][A-Z0-9_]*=' "$TMP_FILE" && die "$svc: env-map có tên biến không hợp lệ"
    mv "$TMP_FILE" "$APP_DIR/env/$svc.env"
    TMP_FILE=""
    log "env/$svc.env: $(cut -d= -f1 "$APP_DIR/env/$svc.env" | paste -sd, -)"   # chỉ in TÊN biến
}

# ---------- 3. file deploy đúng commit ----------
# images.env cũ (trước khi có GIT_SHA) → giữ file đang có trên server.
sync_deploy_files() {
    local images_env=$1 sha dir f
    sha=$(sed -nE 's/^GIT_SHA=//p' "$images_env")
    if [[ -z $sha ]]; then
        log "images.env không có GIT_SHA → dùng file deploy đang có trên server"
        return 0
    fi
    dir=$(mktemp -d)
    for f in "${DEPLOY_FILES[@]}"; do
        mkdir -p "$dir/$(dirname "$f")"
        if ! curl -fsSL --retry 3 -o "$dir/$f" "$REPO_RAW/$sha/deploy/$f"; then
            rm -rf "$dir"
            log "không tải được deploy/$f @ ${sha:0:7}"
            return 1
        fi
    done
    install -m 644 "$dir/compose.app.yml" "$APP_DIR/compose.app.yml"
    install -d -m 755 "$APP_DIR/postgres/init"
    install -m 755 "$dir/postgres/init/01-create-service-databases.sh" "$APP_DIR/postgres/init/"
    rm -rf "$dir"
    log "file deploy @ ${sha:0:7}"
}

# Mạng nhà → GHCR đôi khi đứt giữa chừng ("connection reset by peer", qua IPv6 tới CDN của GHCR); pull là bước
# an toàn để thử lại (chưa đụng container nào). Layer đã tải xong được giữ → lần sau chỉ tải phần còn thiếu.
# 2026-11-21: đứt liên tục ~2 phút → 3 lần × 10 s không đủ, deploy rollback. Giờ: 5 lần, chờ tăng dần (~3 phút),
# kéo tối đa 2 image song song (ít kết nối lớn cùng lúc). Hết lượt vẫn lỗi → coi là deploy lỗi.
readonly PULL_BACKOFF=(15 30 60 90)
pull_images() {
    local images_env=$1 attempt
    for attempt in 1 2 3 4 5; do
        COMPOSE_PARALLEL_LIMIT=2 compose "$images_env" pull --quiet && return 0
        log "pull lỗi (lần $attempt/5)"
        [[ $attempt -lt 5 ]] && sleep "${PULL_BACKOFF[$((attempt - 1))]}"
    done
    return 1
}

# ---------- 4. chạy ----------
# Gọi trong `if` → set -e KHÔNG có hiệu lực bên trong; nối bằng && để bước nào lỗi là cả hàm lỗi.
up() {
    local images_env=$1
    sync_deploy_files "$images_env" \
        && pull_images "$images_env" \
        && compose "$images_env" up -d --remove-orphans --wait --wait-timeout 300 \
        && curl -fsS --retry 5 --retry-delay 2 --retry-all-errors -o /dev/null "$SMOKE_URL"
}

main() {
    [[ $EUID -eq 0 ]] || die "phải chạy bằng root (sudo)"
    [[ $# -eq 1 && ( $1 == - || -f $1 ) ]] || die "cách dùng: deploy.sh <images.env> | deploy.sh - < images.env"

    exec 9>/run/lock/unbutu-deploy.lock
    flock -n 9 || die "đang có lần deploy khác chạy"

    umask 077
    install -d -m 700 "$APP_DIR/env" "$APP_DIR/releases"

    local new="$APP_DIR/releases/images.$(date -u +%Y%m%dT%H%M%SZ).env"
    TMP_FILE=$new                                     # xoá nếu không hợp lệ
    if [[ $1 == - ]]; then
        head -c 4096 >"$new"                          # images.env chỉ vài dòng; chặn input lớn bất thường
    else
        install -m 600 "$1" "$new"
    fi
    validate_images_env "$new"
    TMP_FILE=""
    local version; version=$(app_version "$new")
    log "deploy $ENVIRONMENT phiên bản $version"

    local svc
    for svc in "${INFRA_SERVICES[@]}" $(app_services "$new"); do
        render_env "$svc"
    done

    local current="$APP_DIR/images.current.env"
    if up "$new"; then
        [[ -f $current ]] && cp -p "$current" "$APP_DIR/images.previous.env"
        cp -p "$new" "$current"
        printf '%s ok %s\n' "$(date -u +%FT%TZ)" "$version" >>"$APP_DIR/deploy-history.log"
        log "THÀNH CÔNG: $ENVIRONMENT đang chạy $version"
        return 0
    fi

    printf '%s fail %s\n' "$(date -u +%FT%TZ)" "$version" >>"$APP_DIR/deploy-history.log"
    compose "$new" ps --all >&2 || true
    if [[ -f $current ]]; then
        log "deploy $version lỗi → rollback về $(app_version "$current")"
        up "$current" || die "rollback cũng lỗi – cần xử lý tay (runbook)"
        die "deploy $version lỗi, đã rollback về $(app_version "$current")"
    fi
    die "deploy $version lỗi, không có bản trước để rollback"
}

main "$@"
