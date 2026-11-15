CREATE TABLE events (
    id          UUID PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Dữ liệu mẫu
INSERT INTO events (id, title, type, description, created_at) VALUES
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0001', 'Dune: Part Three', 'MOVIE', 'Suất chiếu IMAX', now() - interval '2 day'),
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0002', 'Đêm nhạc Trịnh Công Sơn', 'CONCERT', 'Nhà hát Hòa Bình', now() - interval '1 day'),
    ('7b0c2f4e-1f7a-4a39-9a3e-2d6f0f1c0003', 'V.League: Hà Nội vs HAGL', 'SPORT', 'Sân Hàng Đẫy', now());
