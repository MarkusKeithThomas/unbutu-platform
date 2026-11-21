#!/usr/bin/env bash
# Sinh /opt/unbutu-platform/observability/env/alert-relay.env từ AWS (ADR-0004), cùng cách deploy.sh làm cho service prod:
# env-map SSM /unbutu/prod/alert-relay/env-map (khai báo ở pro-ticket-workload) + value trong Secrets Manager.
# Chạy trên server bằng root (đọc /root/.aws của IAM user unbutu-server):
#   sudo bash /opt/unbutu-platform/observability/signoz/alert-relay/render-env.sh
# Chỉ in TÊN biến, không bao giờ in value.
set -euo pipefail

readonly SVC=alert-relay
readonly OUT_DIR=/opt/unbutu-platform/observability/env
export AWS_REGION=ap-southeast-1

die() { echo "LỖI: $*" >&2; exit 1; }
[[ $EUID -eq 0 ]] || die "chạy bằng sudo"

umask 077
mkdir -p "$OUT_DIR"
TMP=$(mktemp "$OUT_DIR/.$SVC.XXXXXX")
trap 'rm -f "$TMP"' EXIT

map=$(aws ssm get-parameter --name "/unbutu/prod/$SVC/env-map" --query Parameter.Value --output text) \
    || die "không đọc được env-map của $SVC (đã merge + apply pro-ticket-workload chưa?)"
jq -e '(.env | type == "object") and (.secrets | type == "object")' <<<"$map" >/dev/null || die "env-map sai cấu trúc"

jq -r '.env | to_entries[] | "\(.key)=\(.value)"' <<<"$map" >"$TMP"
declare -A cache=()
while IFS=$'\t' read -r name secret key; do
    if [[ -z ${cache[$secret]+x} ]]; then
        cache[$secret]=$(aws secretsmanager get-secret-value --secret-id "$secret" --query SecretString --output text) \
            || die "không đọc được secret $secret"
    fi
    value=$(jq -er --arg k "$key" '.[$k] | strings' <<<"${cache[$secret]}") || die "secret $secret thiếu key $key"
    [[ -n $value && $value != *$'\n'* ]] || die "value của $name rỗng hoặc nhiều dòng"
    printf '%s=%s\n' "$name" "$value" >>"$TMP"
done < <(jq -r '.secrets | to_entries[] | [.key, .value.secret, .value.key] | @tsv' <<<"$map")

grep -vqE '^[A-Z_][A-Z0-9_]*=' "$TMP" && die "env-map có tên biến không hợp lệ"
mv "$TMP" "$OUT_DIR/$SVC.env"
echo "env/$SVC.env: $(cut -d= -f1 "$OUT_DIR/$SVC.env" | paste -sd, -)"
