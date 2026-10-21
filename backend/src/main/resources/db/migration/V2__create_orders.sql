CREATE TABLE orders (
    id             TEXT    NOT NULL PRIMARY KEY,
    customer_name  TEXT    NOT NULL,
    customer_phone TEXT    NOT NULL,
    street         TEXT    NOT NULL,
    number         TEXT    NOT NULL,
    complement     TEXT,
    neighborhood   TEXT    NOT NULL,
    city           TEXT    NOT NULL,
    state          TEXT    NOT NULL,
    zip_code       TEXT    NOT NULL,
    status         TEXT    NOT NULL,
    total_cents    INTEGER NOT NULL,
    created_by     TEXT    NOT NULL,
    created_at     TEXT    NOT NULL,
    updated_at     TEXT    NOT NULL,
    version        INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT ck_orders_status
        CHECK (status IN ('RECEBIDO', 'EM_PREPARO', 'SAIU_PARA_ENTREGA', 'ENTREGUE', 'CANCELADO')),
    CONSTRAINT ck_orders_total CHECK (total_cents >= 0),
    CONSTRAINT fk_orders_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE order_items (
    id               TEXT    NOT NULL PRIMARY KEY,
    order_id         TEXT    NOT NULL,
    name             TEXT    NOT NULL,
    quantity         INTEGER NOT NULL,
    unit_price_cents INTEGER NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_items_price CHECK (unit_price_cents >= 0),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE TABLE order_status_history (
    id          TEXT NOT NULL PRIMARY KEY,
    order_id    TEXT NOT NULL,
    from_status TEXT,
    to_status   TEXT NOT NULL,
    changed_by  TEXT NOT NULL,
    changed_at  TEXT NOT NULL,
    CONSTRAINT ck_history_from_status
        CHECK (from_status IS NULL
               OR from_status IN ('RECEBIDO', 'EM_PREPARO', 'SAIU_PARA_ENTREGA', 'ENTREGUE', 'CANCELADO')),
    CONSTRAINT ck_history_to_status
        CHECK (to_status IN ('RECEBIDO', 'EM_PREPARO', 'SAIU_PARA_ENTREGA', 'ENTREGUE', 'CANCELADO')),
    CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_history_changed_by FOREIGN KEY (changed_by) REFERENCES users (id)
);

CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);
