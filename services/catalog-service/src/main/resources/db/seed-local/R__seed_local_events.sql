-- Dữ liệu mẫu CHỈ cho local + test (KHÔNG chạy ở prod).
-- Flyway chỉ quét thư mục này khi spring.flyway.locations có classpath:db/seed-local
-- (application-local.yml, compose.local.yml, integration test).
-- Ngày diễn ra tính tương đối theo now() → sự kiện luôn "sắp diễn ra" (test không hỏng theo thời gian).
-- R__ = repeatable: chạy lại khi nội dung file đổi → dùng ON CONFLICT để không trùng.
INSERT INTO events (id, title, type, description, venue, starts_at, created_at) VALUES
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0001', 'Dune: Part Three', 'MOVIE', 'Suất chiếu IMAX', 'CGV Vincom Bà Triệu', date_trunc('day', now()) + interval '27 day 12 hour 30 minute', now() - interval '2 day'),
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'Đêm nhạc Trịnh Công Sơn', 'CONCERT', 'Đêm nhạc 20/12', 'Nhà hát Hòa Bình', date_trunc('day', now()) + interval '29 day 13 hour', now() - interval '1 day'),
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0003', 'V.League: Hà Nội vs HAGL', 'SPORT', 'Vòng 10', 'Sân Hàng Đẫy', date_trunc('day', now()) + interval '52 day 11 hour', now())
ON CONFLICT (id) DO UPDATE SET venue = EXCLUDED.venue, starts_at = EXCLUDED.starts_at, description = EXCLUDED.description;

INSERT INTO ticket_types (id, event_id, name, description, price, total_quantity, status, first_on_sale_at) VALUES
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0001', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'VIP',             'Khu ngồi sát sân khấu, quà tặng', 2500000,  500, 'ON_SALE', now()),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0002', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'Hạng A',          'Khu ngồi trung tâm',              1500000, 2000, 'ON_SALE', now()),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0006', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'Hạng B',          'Khu ngồi hai bên',                 900000, 5000, 'ON_SALE', now()),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0003', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'Hạng C · Đứng',   'Khu đứng phía sau',                500000, 3000, 'PAUSED',  now()),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0004', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0001', 'IMAX tiêu chuẩn', NULL,                               180000,  300, 'DRAFT',   NULL),
    ('5d1e8a90-0b6c-4c1f-8f0e-6a2b3c4d0005', '7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0003', 'Khán đài A',      'Khán đài có mái che',              200000, 4000, 'ON_SALE', now())
ON CONFLICT (id) DO UPDATE SET description = EXCLUDED.description;
