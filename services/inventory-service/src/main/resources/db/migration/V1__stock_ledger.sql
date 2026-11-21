-- Sổ cái tồn kho (nguồn sự thật bền). Redis chỉ giữ bộ đếm "còn lại" + vé đang giữ (TTL) để chống oversell nhanh;
-- Redis mất dữ liệu → dựng lại: còn lại = total_quantity - sold.
CREATE TABLE stock (
    ticket_type_id     UUID        PRIMARY KEY,
    event_id           UUID        NOT NULL,
    total_quantity     INTEGER     NOT NULL,
    sold               INTEGER     NOT NULL DEFAULT 0 CHECK (sold >= 0),
    status             VARCHAR(16) NOT NULL,            -- DRAFT | ON_SALE | PAUSED (từ catalog)
    deleted            BOOLEAN     NOT NULL DEFAULT false,
    catalog_updated_at TIMESTAMPTZ NOT NULL,            -- version snapshot từ catalog: chỉ nhận bản mới hơn
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_stock_event ON stock (event_id) WHERE NOT deleted;
