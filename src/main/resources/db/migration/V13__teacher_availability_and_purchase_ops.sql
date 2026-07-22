ALTER TABLE teacher_availability
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE teacher_availability
ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE teacher_availability
ADD CONSTRAINT chk_teacher_availability_status CHECK (status IN ('ACTIVE', 'CANCELLED'));

CREATE INDEX idx_teacher_availability_status_time ON teacher_availability (teacher_id, status, start_at, end_at);

ALTER TABLE course_purchases
ADD COLUMN admin_note TEXT;

CREATE TABLE purchase_events (
    id BIGSERIAL PRIMARY KEY,
    purchase_id BIGINT NOT NULL,
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchase_events_purchase FOREIGN KEY (purchase_id) REFERENCES course_purchases(id) ON DELETE CASCADE,
    CONSTRAINT chk_purchase_events_old_status CHECK (old_status IS NULL OR old_status IN ('PENDING', 'PAID', 'CANCELLED', 'FAILED')),
    CONSTRAINT chk_purchase_events_new_status CHECK (new_status IN ('PENDING', 'PAID', 'CANCELLED', 'FAILED'))
);

CREATE INDEX idx_purchase_events_purchase_time ON purchase_events (purchase_id, created_at);
