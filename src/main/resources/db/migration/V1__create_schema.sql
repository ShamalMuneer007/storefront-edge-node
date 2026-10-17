CREATE TABLE product (
    id                   UUID PRIMARY KEY,
    sku                  VARCHAR(64)    NOT NULL UNIQUE,
    name                 VARCHAR(200)   NOT NULL,
    unit_price           NUMERIC(19, 4) NOT NULL CHECK (unit_price >= 0),
    currency             CHAR(3)        NOT NULL,
    tax_rate             NUMERIC(5, 2)  NOT NULL CHECK (tax_rate BETWEEN 0 AND 100),
    requires_preparation BOOLEAN        NOT NULL,
    stock                INTEGER        NOT NULL CHECK (stock >= 0),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE customer_order (
    id              UUID PRIMARY KEY,
    branch_code     VARCHAR(10)    NOT NULL,
    terminal_id     VARCHAR(64)    NOT NULL,
    business_date   DATE           NOT NULL,
    fiscal_sequence INTEGER        NOT NULL,
    fiscal_number   VARCHAR(40)    NOT NULL,
    currency        CHAR(3)        NOT NULL,
    subtotal        NUMERIC(19, 4) NOT NULL,
    tax             NUMERIC(19, 4) NOT NULL,
    total           NUMERIC(19, 4) NOT NULL,
    placed_at       TIMESTAMPTZ    NOT NULL,
    UNIQUE (branch_code, business_date, fiscal_sequence)
);

CREATE TABLE order_line (
    order_id             UUID           NOT NULL REFERENCES customer_order (id),
    line_no              SMALLINT       NOT NULL,
    sku                  VARCHAR(64)    NOT NULL,
    name                 VARCHAR(200)   NOT NULL,
    quantity             INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price           NUMERIC(19, 4) NOT NULL,
    tax_rate             NUMERIC(5, 2)  NOT NULL,
    requires_preparation BOOLEAN        NOT NULL,
    PRIMARY KEY (order_id, line_no)
);

CREATE TABLE fiscal_sequence (
    branch_code   VARCHAR(10) NOT NULL,
    business_date DATE        NOT NULL,
    last_value    INTEGER     NOT NULL,
    PRIMARY KEY (branch_code, business_date)
);

CREATE TABLE idempotency_key (
    terminal_id     VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    order_id        UUID        NOT NULL REFERENCES customer_order (id) DEFERRABLE INITIALLY DEFERRED,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (terminal_id, idempotency_key)
);

CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY,
    aggregate_id    UUID        NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts        INTEGER     NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX outbox_event_pending_idx ON outbox_event (next_attempt_at) WHERE published_at IS NULL;

CREATE TABLE kitchen_ticket (
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL UNIQUE,
    event_id   UUID        NOT NULL,
    payload    JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
