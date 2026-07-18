ALTER TABLE enrollments
    ADD COLUMN enrolled_at TIMESTAMP;

ALTER TABLE enrollments
    ADD COLUMN expired_at TIMESTAMP;

CREATE TABLE course_purchases (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    enrollment_id BIGINT,
    amount DECIMAL(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    program_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    CONSTRAINT fk_course_purchases_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_purchases_program FOREIGN KEY (program_id) REFERENCES programs(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_purchases_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX idx_course_purchases_enrollment ON course_purchases (enrollment_id);
CREATE INDEX idx_course_purchases_student ON course_purchases (student_id);
CREATE INDEX idx_course_purchases_program ON course_purchases (program_id);
CREATE INDEX idx_course_purchases_status ON course_purchases (status);
