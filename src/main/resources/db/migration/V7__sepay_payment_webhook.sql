ALTER TABLE course_purchases
    ADD COLUMN payment_code VARCHAR(50);

ALTER TABLE course_purchases
    ADD COLUMN payment_qr_url VARCHAR(1000);

ALTER TABLE course_purchases
    ADD COLUMN payment_provider VARCHAR(30) NOT NULL DEFAULT 'SEPAY';

ALTER TABLE course_purchases
    ADD COLUMN provider_transaction_id VARCHAR(50);

ALTER TABLE course_purchases
    ADD COLUMN provider_reference_code VARCHAR(100);

ALTER TABLE course_purchases
    ADD COLUMN payment_content VARCHAR(255);

CREATE UNIQUE INDEX idx_course_purchases_payment_code ON course_purchases (payment_code);
CREATE UNIQUE INDEX idx_course_purchases_provider_transaction ON course_purchases (provider_transaction_id);

CREATE TABLE sepay_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    sepay_transaction_id BIGINT NOT NULL UNIQUE,
    payment_code VARCHAR(50),
    reference_code VARCHAR(100),
    account_number VARCHAR(50),
    transfer_amount DECIMAL(12, 2),
    status VARCHAR(20) NOT NULL,
    reason VARCHAR(500),
    raw_payload TEXT NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sepay_webhook_events_payment_code ON sepay_webhook_events (payment_code);
CREATE INDEX idx_sepay_webhook_events_status ON sepay_webhook_events (status);
