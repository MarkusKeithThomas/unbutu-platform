# Runbook – thêm database cho service mới trên prod

Postgres prod chỉ chạy `deploy/postgres/init/` **1 lần** (lúc volume trống) → service mới (vd. `inventory`) phải tạo
user + database bằng tay **một lần**, TRƯỚC lần deploy đầu tiên của service đó. Password lấy thẳng từ Secrets Manager
qua pipe – không in ra màn hình, không nằm trong lịch sử shell.

## Điều kiện
1. Secret `unbutu/prod/<svc>-service` có key `DB_PASSWORD` (người dùng tạo trên AWS Console).
2. PR `pro-ticket-workload` thêm `prod/services/<svc>-service/env.yaml` + `postgres` (`SERVICE_DATABASES`, `<SVC>_DB_PASSWORD`) đã merge + apply.

## Lệnh (vd. svc=inventory)
```bash
ssh unbutu 'sudo bash -s' <<'EOS'
set -euo pipefail
svc=inventory
aws --region ap-southeast-1 secretsmanager get-secret-value --secret-id "unbutu/prod/${svc}-service" \
    --query SecretString --output text | jq -er .DB_PASSWORD |
docker exec -i prod-postgres-1 bash -c '
  read -r pw
  psql -v ON_ERROR_STOP=1 -U postgres -d postgres -v svc="$0" -v db="$0_db" -v pw="$pw" <<SQL
CREATE USER :"svc" WITH PASSWORD :'"'"'pw'"'"';
CREATE DATABASE :"db" OWNER :"svc";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
SQL' "$svc"
EOS
```

## Kiểm tra
```bash
ssh unbutu 'docker exec prod-postgres-1 psql -U postgres -tAc "select datname from pg_database where datname = '"'"'inventory_db'"'"'"'
```
Sau đó mới merge PR service → deploy (Flyway của service tự tạo bảng).
