-- V1 tạo token_hash CHAR(64) nhưng entity map String → VARCHAR ⇒ Hibernate (ddl-auto=validate) không khởi động
-- (deploy f407741/f00b70f rollback). V1 đã chạy trên prod → sửa bằng migration mới, KHÔNG sửa V1 (checksum Flyway).
-- SHA-256 hex luôn đủ 64 ký tự nên đổi kiểu không làm thay đổi dữ liệu.
ALTER TABLE refresh_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);
