ALTER TABLE programs
    ADD COLUMN final_assessment_retake_price DECIMAL(12, 2) NOT NULL DEFAULT 0;

DROP INDEX idx_enrollments_student_active;

ALTER TABLE enrollments
    DROP COLUMN active_student_id;

ALTER TABLE enrollments
    ALTER COLUMN status TYPE VARCHAR(30);

ALTER TABLE enrollments
    ADD COLUMN active_student_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN student_id ELSE NULL END) ${generatedStored};

CREATE UNIQUE INDEX idx_enrollments_student_active ON enrollments (active_student_id);

CREATE TABLE final_assessment_sessions (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL,
    evaluator_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    meet_link VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_final_assessment_sessions_program FOREIGN KEY (program_id) REFERENCES programs(id) ON DELETE CASCADE,
    CONSTRAINT fk_final_assessment_sessions_evaluator FOREIGN KEY (evaluator_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_final_assessment_sessions_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_final_assessment_sessions_program ON final_assessment_sessions (program_id, scheduled_at);
CREATE INDEX idx_final_assessment_sessions_evaluator ON final_assessment_sessions (evaluator_id, scheduled_at);
CREATE INDEX idx_final_assessment_sessions_status ON final_assessment_sessions (status);

CREATE TABLE final_assessment_retake_payments (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_code VARCHAR(50) UNIQUE,
    payment_qr_url VARCHAR(1000),
    payment_provider VARCHAR(30) NOT NULL DEFAULT 'SEPAY',
    provider_transaction_id VARCHAR(50) UNIQUE,
    provider_reference_code VARCHAR(100),
    payment_content VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    CONSTRAINT fk_final_retake_payments_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE,
    CONSTRAINT fk_final_retake_payments_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_final_retake_payments_program FOREIGN KEY (program_id) REFERENCES programs(id) ON DELETE CASCADE,
    CONSTRAINT chk_final_retake_payments_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED', 'FAILED'))
);

CREATE INDEX idx_final_retake_payments_enrollment ON final_assessment_retake_payments (enrollment_id);
CREATE INDEX idx_final_retake_payments_student ON final_assessment_retake_payments (student_id);
CREATE INDEX idx_final_retake_payments_code ON final_assessment_retake_payments (payment_code);
CREATE INDEX idx_final_retake_payments_status ON final_assessment_retake_payments (status);

CREATE TABLE final_assessment_participants (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    retake_payment_id BIGINT,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_final_assessment_participants_session FOREIGN KEY (session_id) REFERENCES final_assessment_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_final_assessment_participants_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE,
    CONSTRAINT fk_final_assessment_participants_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_final_assessment_participants_retake_payment FOREIGN KEY (retake_payment_id) REFERENCES final_assessment_retake_payments(id) ON DELETE SET NULL,
    CONSTRAINT uk_final_assessment_participants_session_enrollment UNIQUE (session_id, enrollment_id),
    CONSTRAINT uk_final_assessment_participants_retake_payment UNIQUE (retake_payment_id)
);

CREATE INDEX idx_final_assessment_participants_student ON final_assessment_participants (student_id);
CREATE INDEX idx_final_assessment_participants_enrollment ON final_assessment_participants (enrollment_id);

CREATE TABLE final_assessment_results (
    id BIGSERIAL PRIMARY KEY,
    participant_id BIGINT NOT NULL UNIQUE,
    evaluator_id BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL,
    comment TEXT,
    evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_final_assessment_results_participant FOREIGN KEY (participant_id) REFERENCES final_assessment_participants(id) ON DELETE CASCADE,
    CONSTRAINT fk_final_assessment_results_evaluator FOREIGN KEY (evaluator_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_final_assessment_results_result CHECK (result IN ('PASS', 'NOT_PASS'))
);

CREATE INDEX idx_final_assessment_results_evaluator ON final_assessment_results (evaluator_id, evaluated_at);
