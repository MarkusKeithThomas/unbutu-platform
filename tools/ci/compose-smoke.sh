#!/usr/bin/env bash
# Smoke test "như deploy prod" chạy trong CI – lấp khoảng hở "CI xanh nhưng deploy lỗi".
#
# Chạy ĐÚNG deploy/compose.app.yml (file deploy.sh dùng trên server) với:
#   - image vừa build trong CI (<service>:ci, frontend:ci) thay cho image GHCR theo digest
#   - env/<service>.env cùng TÊN biến với env-map prod (pro-ticket-workload/prod/services/*/env.yaml),
#     value là giá trị test sinh tại chỗ (JWT key cùng định dạng prod: PKCS#8 DER base64 một dòng)
#   - `docker compose up --wait` giống deploy.sh → container nào unhealthy là FAIL (như rollback trên prod)
# rồi gọi API thật qua api-gateway (127.0.0.1:8080): catalog, UI, đăng ký → đăng nhập → refresh → /me.
#
# Cách dùng (sau khi đã docker build các image :ci):  tools/ci/compose-smoke.sh
set -euo pipefail

cd "$(dirname "$0")/../.."
readonly PROJECT=ci-smoke
readonly COMPOSE=(docker compose -p "$PROJECT" -f deploy/compose.app.yml)
readonly BASE=http://127.0.0.1:8080

log() { printf '\n==> %s\n' "$*"; }
fail() { printf '\n::error::%s\n' "$*"; exit 1; }

cleanup() {
    local code=$?
    if [[ $code -ne 0 ]]; then
        log "LỖI – trạng thái container + log"
        "${COMPOSE[@]}" ps --all || true
        "${COMPOSE[@]}" logs --no-color --tail 150 || true
    fi
    "${COMPOSE[@]}" down -v --remove-orphans >/dev/null 2>&1 || true
    rm -rf deploy/env
    exit "$code"
}
trap cleanup EXIT

# ---------- 1. image: <SVC>_IMAGE=<svc>:ci (deploy.sh dùng images.env cùng tên biến) ----------
for dir in services/*/; do
    svc=$(basename "$dir")
    [[ -f $dir/build.gradle.kts ]] || continue
    export "$(echo "$svc" | tr 'a-z-' 'A-Z_')_IMAGE=$svc:ci"
done
export FRONTEND_IMAGE=frontend:ci APP_VERSION=ci

# ---------- 2. env/<service>.env – cùng tên biến với env-map prod ----------
rand() { openssl rand -hex 16; }
DB_CATALOG=$(rand); DB_IDENTITY=$(rand)
JWT_KEY=$(openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -outform DER 2>/dev/null | base64 | tr -d '\n')
mkdir -p deploy/env
cat >deploy/env/postgres.env <<ENV
POSTGRES_USER=postgres
POSTGRES_PASSWORD=$(rand)
SERVICE_DATABASES=catalog identity
CATALOG_DB_PASSWORD=$DB_CATALOG
IDENTITY_DB_PASSWORD=$DB_IDENTITY
ENV
cat >deploy/env/catalog-service.env <<ENV
ENVIRONMENT=prod
DB_URL=jdbc:postgresql://postgres:5432/catalog_db
DB_USERNAME=catalog
DB_PASSWORD=$DB_CATALOG
ENV
cat >deploy/env/identity-service.env <<ENV
ENVIRONMENT=prod
DB_URL=jdbc:postgresql://postgres:5432/identity_db
DB_USERNAME=identity
DB_PASSWORD=$DB_IDENTITY
JWT_PRIVATE_KEY=$JWT_KEY
UI_BASE_URL=http://127.0.0.1:8080
GOOGLE_REDIRECT_URI=http://127.0.0.1:8080/api/identity/login/oauth2/code/google
ENV
cat >deploy/env/api-gateway.env <<ENV
ENVIRONMENT=prod
CATALOG_SERVICE_URL=http://catalog-service:8080
IDENTITY_SERVICE_URL=http://identity-service:8080
FRONTEND_SERVICE_URL=http://frontend:8080
ENV
: >deploy/env/frontend.env

# Network do project observability tạo trên server (external) – CI tạo rỗng để compose chạy được.
docker network inspect telemetry >/dev/null 2>&1 || docker network create telemetry >/dev/null

# ---------- 3. up --wait như deploy.sh ----------
log "docker compose up --wait (compose.app.yml)"
"${COMPOSE[@]}" config --quiet
"${COMPOSE[@]}" up -d --wait --wait-timeout 300
"${COMPOSE[@]}" ps

# ---------- 4. gọi API thật qua gateway ----------
# expect <mô tả> <HTTP code mong đợi> <curl args...>  → in body vào $BODY, header vào $HEADERS
expect() {
    local what=$1 want=$2; shift 2
    local out code
    out=$(curl -sS -D /tmp/smoke-headers -o /tmp/smoke-body -w '%{http_code}' "$@") || fail "$what: curl lỗi"
    code=$out
    BODY=$(cat /tmp/smoke-body); HEADERS=$(cat /tmp/smoke-headers)
    [[ $code == "$want" ]] || fail "$what: HTTP $code (mong đợi $want) – body: ${BODY:0:300}"
    echo "  OK  $what → $code"
}

log "Smoke test qua api-gateway $BASE"
expect "catalog ping"                200 "$BASE/api/catalog/ping"
expect "catalog events"              200 "$BASE/api/catalog/events"
expect "UI /login (frontend qua gateway)" 200 "$BASE/login"
grep -q '<div id="root">' <<<"$BODY" || fail "UI /login không trả index.html của SPA"
expect "API không tồn tại → 404 (không rơi vào SPA)" 404 "$BASE/api/khong-ton-tai"
expect "identity providers"          200 "$BASE/api/identity/providers"

EMAIL="smoke-$(date +%s)-$RANDOM@example.com"
JSON=(-H 'Content-Type: application/json')
expect "đăng ký"                     201 "${JSON[@]}" -X POST "$BASE/api/identity/register" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"smoke-pass-123\",\"fullName\":\"CI Smoke\"}"
expect "đăng ký trùng email → 409"   409 "${JSON[@]}" -X POST "$BASE/api/identity/register" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"smoke-pass-123\"}"
expect "sai mật khẩu → 401"          401 "${JSON[@]}" -X POST "$BASE/api/identity/login" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"wrong-pass-123\"}"
expect "đăng nhập"                   200 "${JSON[@]}" -X POST "$BASE/api/identity/login" \
    -d "{\"email\":\"$EMAIL\",\"password\":\"smoke-pass-123\",\"rememberMe\":true}"

# Cookie Secure (prod) → curl không tự gửi qua http; lấy giá trị từ Set-Cookie rồi gửi tay.
REFRESH=$(grep -i '^set-cookie: refresh_token=' <<<"$HEADERS" | sed -E 's/^[^=]*=([^;]*).*/\1/' | tr -d '\r')
[[ -n $REFRESH ]] || fail "đăng nhập không đặt cookie refresh_token"
grep -i '^set-cookie: refresh_token=' <<<"$HEADERS" | grep -qi 'httponly' || fail "cookie refresh_token thiếu HttpOnly"

expect "refresh (xoay vòng token)"   200 -X POST -H "Cookie: refresh_token=$REFRESH" "$BASE/api/identity/refresh"
ACCESS=$(jq -er .accessToken <<<"$BODY") || fail "refresh không trả accessToken"
expect "refresh token cũ dùng lại → 401" 401 -X POST -H "Cookie: refresh_token=$REFRESH" "$BASE/api/identity/refresh"

expect "/me có token"                200 -H "Authorization: Bearer $ACCESS" "$BASE/api/identity/me"
[[ $(jq -r .email <<<"$BODY") == "$EMAIL" ]] || fail "/me trả sai email"
expect "/me không token → 401"       401 "$BASE/api/identity/me"

log "SMOKE TEST ĐẠT – stack compose.app.yml chạy được như deploy"
