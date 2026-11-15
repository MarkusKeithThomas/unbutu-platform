-- Chạy 1 lần khi khởi tạo volume Postgres: mỗi service 1 database + user riêng.
-- Mật khẩu thật đặt qua .env; file này chỉ tạo database.
CREATE DATABASE identity_db;
CREATE DATABASE catalog_db;
CREATE DATABASE inventory_db;
CREATE DATABASE booking_db;
CREATE DATABASE payment_db;
CREATE DATABASE notification_db;
