CREATE TABLE events (
    id          UUID PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Dữ liệu mẫu KHÔNG nằm ở đây (sẽ lên prod) – xem db/seed-local/.
