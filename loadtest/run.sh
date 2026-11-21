#!/usr/bin/env bash
# Chạy load test k6 theo bậc + web dashboard trực tiếp (gọi qua `make loadtest …`, xem docs/runbooks/loadtest.md).
#
#   TARGET=prod  (mặc định) k6 chạy TRÊN SERVER trong mạng prod_default, gọi thẳng http://api-gateway:8080.
#                Dashboard: container publish 127.0.0.1:5665 trên server (chỉ trong lúc chạy) + ssh -L → Mac localhost:5665.
#   TARGET=local k6 chạy trên Mac, gọi stack local http://host.docker.internal:8080 (make up).
#
# Ctrl+C = dừng test (k6 vẫn in bảng kết quả + lưu báo cáo). Mỗi lúc chỉ 1 lượt (container tên cố định k6-loadtest).
# Kết quả: loadtest/reports/<scenario>-<thời gian>.{html,json} (báo cáo HTML của dashboard + bảng bậc dạng JSON).
set -euo pipefail

readonly K6_IMAGE=grafana/k6:1.8.2
readonly PORT=5665
cd "$(dirname "$0")/.."

SCENARIO=${SCENARIO:-browse}
TARGET=${TARGET:-prod}
NAME="$SCENARIO-$(date +%Y%m%d-%H%M%S)"

die() { echo "LỖI: $*" >&2; exit 1; }

# Kiểm tra đầu vào (giá trị được ghép vào lệnh chạy trên server → chỉ nhận định dạng an toàn).
[[ $SCENARIO =~ ^[a-z0-9-]+$ && -f loadtest/k6/$SCENARIO.js ]] || die "không có kịch bản loadtest/k6/$SCENARIO.js"
[[ -z ${STEPS:-} || $STEPS =~ ^[0-9]+(,[0-9]+)*$ ]] || die "STEPS dạng 50,100,200"
[[ -z ${STEP_TIME:-} || $STEP_TIME =~ ^[0-9]+[sm]$ ]] || die "STEP_TIME dạng 30s hoặc 2m"
[[ -z ${P95_MS:-} || $P95_MS =~ ^[0-9]+$ ]] || die "P95_MS là số nguyên (ms)"
[[ -z ${MAX_ERR_PCT:-} || $MAX_ERR_PCT =~ ^[0-9]+(\.[0-9]+)?$ ]] || die "MAX_ERR_PCT là số"
[[ -z ${STOP_ON_FAIL:-} || $STOP_ON_FAIL =~ ^(true|false)$ ]] || die "STOP_ON_FAIL là true/false"
[[ -z ${MAX_RPS:-} || $MAX_RPS =~ ^[0-9]+$ ]] || die "MAX_RPS là số nguyên"

envs=(-e "SCENARIO=$SCENARIO" -e "OUT_NAME=$NAME"
      -e K6_WEB_DASHBOARD=true -e K6_WEB_DASHBOARD_HOST=0.0.0.0 -e "K6_WEB_DASHBOARD_PORT=$PORT"
      -e K6_WEB_DASHBOARD_PERIOD=2s -e "K6_WEB_DASHBOARD_EXPORT=/out/$NAME.html")
for v in STEPS STEP_TIME P95_MS MAX_ERR_PCT STOP_ON_FAIL MAX_RPS; do
    [[ -n ${!v:-} ]] && envs+=(-e "$v=${!v}")
done

mkdir -p loadtest/reports
echo ">> Kịch bản $SCENARIO · STEPS=${STEPS:-50,100,200} · STEP_TIME=${STEP_TIME:-30s} · TARGET=$TARGET"
echo ">> Dashboard trực tiếp: http://localhost:$PORT  (mở khi thấy dòng 'web dashboard'; Ctrl+C để dừng test)"

case $TARGET in
local)
    docker run --rm -it --name k6-loadtest -p "127.0.0.1:$PORT:$PORT" \
        -v "$PWD/loadtest/k6:/scripts:ro" -v "$PWD/loadtest/reports:/out" --user "$(id -u):$(id -g)" \
        "${envs[@]}" -e BASE_URL=http://host.docker.internal:8080 \
        "$K6_IMAGE" run "/scripts/$SCENARIO.js" || true
    ;;
prod)
    # Chép kịch bản đang có trên máy (kể cả chưa commit) lên ~/loadtest của user ssh. COPYFILE_DISABLE: không kèm file ._* của macOS.
    COPYFILE_DISABLE=1 tar --no-mac-metadata --no-xattrs -C loadtest -cf - k6 \
        | ssh -o BatchMode=yes unbutu 'rm -rf ~/loadtest/k6 && mkdir -p ~/loadtest/out && tar -xf - -C ~/loadtest'
    # --cpus 1: k6 chạy cùng máy với hệ thống bị đo → giới hạn để không tranh hết CPU (ghi rõ khi đọc kết quả).
    remote="docker run --rm -it --name k6-loadtest --network prod_default --cpus 1 --memory 1g"
    remote+=" -p 127.0.0.1:$PORT:$PORT -v \$HOME/loadtest/k6:/scripts:ro -v \$HOME/loadtest/out:/out --user \$(id -u):\$(id -g)"
    remote+=" $(printf '%q ' "${envs[@]}") $K6_IMAGE run /scripts/$SCENARIO.js"
    ssh -t -o ExitOnForwardFailure=yes -L "$PORT:127.0.0.1:$PORT" unbutu "$remote" || true
    scp -q "unbutu:loadtest/out/$NAME.*" loadtest/reports/ 2>/dev/null || echo "(không lấy được báo cáo – xem ~/loadtest/out trên server)"
    ;;
*) die "TARGET là prod hoặc local" ;;
esac

ls loadtest/reports/"$NAME".* 2>/dev/null | sed 's/^/>> Báo cáo: /' || true
