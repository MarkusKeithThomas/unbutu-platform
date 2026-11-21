-- Đơn đã chốt (đã thanh toán) – chốt lại lần 2 (event lặp, retry) → nhận ra ngay, không cộng sold lần nữa.
CREATE TABLE commits (
    reservation_id UUID        PRIMARY KEY,      -- = bookingId
    event_id       UUID        NOT NULL,
    late           BOOLEAN     NOT NULL,          -- true: hold đã hết hạn/nhả, phải lấy lại từ số còn
    committed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
