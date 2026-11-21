-- ===== PHÍA "NGÂN HÀNG" GIẢ LẬP (logic tách biệt với payments; ở cùng DB cho gọn) =====
-- Ngân hàng chỉ biết giao dịch khi khách mở trang thanh toán. querydr đọc từ đây.
CREATE TABLE mockbank_transactions (
    txn_ref         VARCHAR(40) PRIMARY KEY,
    amount          BIGINT      NOT NULL,          -- VND (không ×100)
    order_info      VARCHAR(255),
    bank_code       VARCHAR(20),
    return_url      VARCHAR(500) NOT NULL,
    create_date     VARCHAR(14) NOT NULL,          -- vnp_CreateDate của merchant
    expire_at       TIMESTAMPTZ NOT NULL,
    status          VARCHAR(16) NOT NULL,          -- OPEN | PROCESSING | SUCCESS | FAILED | CANCELLED
    response_code   VARCHAR(4),
    transaction_no  VARCHAR(20),
    pay_date        VARCHAR(14),
    settle_at       TIMESTAMPTZ,                   -- PROCESSING: lúc ngân hàng ra kết quả
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_mockbank_settle ON mockbank_transactions (settle_at) WHERE status = 'PROCESSING';

-- Hàng đợi IPN của ngân hàng (bền: restart không mất; retry theo lịch như VNPay).
CREATE TABLE mockbank_ipn_deliveries (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    txn_ref             VARCHAR(40) NOT NULL REFERENCES mockbank_transactions (txn_ref),
    due_at              TIMESTAMPTZ NOT NULL,
    attempt             INTEGER     NOT NULL DEFAULT 0,
    tamper              VARCHAR(16) NOT NULL DEFAULT 'NONE',   -- NONE | BAD_SIGNATURE | WRONG_AMOUNT
    fake_network_errors INTEGER     NOT NULL DEFAULT 0,        -- giả lập: N lần đầu "mạng lỗi"
    status              VARCHAR(16) NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED | SENDING | DELIVERED | GAVE_UP
    last_result         VARCHAR(200),
    fault_note          VARCHAR(200),                           -- lỗi nào sinh ra lần gửi này (để điều tra)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_mockbank_ipn_due ON mockbank_ipn_deliveries (due_at) WHERE status = 'SCHEDULED';

-- ===== PHÍA MERCHANT: lịch sử đối soát =====
CREATE TABLE reconciliation_log (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id     UUID        NOT NULL,
    txn_ref        VARCHAR(40) NOT NULL,
    gateway_code   VARCHAR(4),             -- vnp_ResponseCode của querydr (00 / 91 / 99 …)
    gateway_status VARCHAR(4),             -- vnp_TransactionStatus (00 thành công / 01 chưa xong / 02 lỗi)
    action         VARCHAR(24) NOT NULL,   -- SUCCEEDED | FAILED | WAIT | ALREADY_FINAL | ERROR | MISMATCH
    note           VARCHAR(300),
    checked_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_reconciliation_payment ON reconciliation_log (payment_id, checked_at DESC);
CREATE INDEX ix_payments_pending_created ON payments (created_at) WHERE status = 'PENDING';

-- vnp_CreateDate lưu cố định lúc tạo giao dịch: bấm lại → cùng URL (byte-for-byte); querydr cần vnp_TransactionDate = giá trị này.
ALTER TABLE payments ADD COLUMN vnp_create_date VARCHAR(14);
UPDATE payments SET vnp_create_date = to_char(created_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYYMMDDHH24MISS') WHERE vnp_create_date IS NULL;
ALTER TABLE payments ALTER COLUMN vnp_create_date SET NOT NULL;
