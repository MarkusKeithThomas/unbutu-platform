-- Sự kiện: thêm địa điểm, thời gian bắt đầu, thời điểm sửa gần nhất.
ALTER TABLE events
    ADD COLUMN venue      VARCHAR(255),
    ADD COLUMN starts_at  TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

-- Mã hiển thị cho admin (TT-0001…), tách khỏi id (UUID) để không lộ/phụ thuộc thứ tự.
CREATE SEQUENCE ticket_type_code_seq;

-- Hạng vé: giá + tổng số vé của 1 sự kiện. Số đã bán/còn lại KHÔNG ở đây (inventory-service sở hữu).
CREATE TABLE ticket_types (
    id               UUID PRIMARY KEY,
    code             VARCHAR(16)  NOT NULL UNIQUE
                     DEFAULT ('TT-' || lpad(nextval('ticket_type_code_seq')::text, 4, '0')),
    event_id         UUID         NOT NULL REFERENCES events (id) ON DELETE RESTRICT,
    name             VARCHAR(100) NOT NULL,
    price            BIGINT       NOT NULL CHECK (price > 0),             -- VND, số nguyên
    total_quantity   INTEGER      NOT NULL CHECK (total_quantity >= 0),
    status           VARCHAR(16)  NOT NULL,                               -- DRAFT | ON_SALE | PAUSED
    first_on_sale_at TIMESTAMPTZ,                                         -- đã từng mở bán → không xoá hẳn
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ticket_types_event_name UNIQUE (event_id, name)
);

CREATE INDEX ix_ticket_types_event ON ticket_types (event_id);
