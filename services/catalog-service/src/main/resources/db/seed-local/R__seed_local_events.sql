-- Dữ liệu mẫu CHỈ cho local + test (KHÔNG chạy ở prod).
-- Flyway chỉ quét thư mục này khi spring.flyway.locations có classpath:db/seed-local
-- (application-local.yml, compose.local.yml, integration test).
-- R__ = repeatable: chạy lại khi nội dung file đổi → dùng ON CONFLICT để không trùng.
INSERT INTO events (id, title, type, description, venue, starts_at, created_at) VALUES
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0001', 'Dune: Part Three', 'MOVIE', 'Suất chiếu IMAX', 'CGV Vincom Bà Triệu', '2026-12-18 19:30+07', now() - interval '2 day'),
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'Đêm nhạc Trịnh Công Sơn', 'CONCERT', 'Đêm nhạc 20/12', 'Nhà hát Hòa Bình', '2026-12-20 20:00+07', now() - interval '1 day'),
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0003', 'V.League: Hà Nội vs HAGL', 'SPORT', 'Vòng 10', 'Sân Hàng Đẫy', '2027-01-12 18:00+07', now())
ON CONFLICT (id) DO UPDATE SET venue = EXCLUDED.venue, starts_at = EXCLUDED.starts_at, description = EXCLUDED.description;

INSERT INTO ticket_types (id, event_id, name, price, total_quantity, status, first_on_sale_at) VALUES
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0001', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'VIP',          2500000,  500, 'ON_SALE', now()),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0002', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'Hạng A',       1500000, 2000, 'ON_SALE', now()),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0003', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'Hạng C · Đứng', 500000, 3000, 'PAUSED',  now()),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0004', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0001', 'IMAX tiêu chuẩn', 180000, 300, 'DRAFT',   NULL),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0005', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0003', 'Khán đài A',    200000, 4000, 'ON_SALE', now())
ON CONFLICT (id) DO NOTHING;
