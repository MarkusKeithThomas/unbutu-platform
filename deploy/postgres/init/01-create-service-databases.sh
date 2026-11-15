#!/bin/bash
# Postgres chạy script này 1 lần, khi volume dữ liệu còn trống (lần khởi tạo đầu tiên).
# Mỗi service trong SERVICE_DATABASES (vd. "catalog identity") được:
#   - user <svc> với mật khẩu lấy từ biến <SVC>_DB_PASSWORD
#   - database <svc>_db do chính user đó sở hữu (Flyway của service tạo bảng trong đó)
#   - chặn user khác kết nối vào database này → service không đọc được bảng của service khác
set -euo pipefail

for svc in ${SERVICE_DATABASES:-}; do
    password_var="${svc^^}_DB_PASSWORD"
    password="${!password_var:?Thiếu biến $password_var}"
    echo "Tạo user '$svc' và database '${svc}_db'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
         -v svc="$svc" -v db="${svc}_db" -v pw="$password" <<'SQL'
CREATE USER :"svc" WITH PASSWORD :'pw';
CREATE DATABASE :"db" OWNER :"svc";
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
SQL
done
