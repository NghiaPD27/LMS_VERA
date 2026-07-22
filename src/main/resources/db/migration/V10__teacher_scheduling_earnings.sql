CREATE TABLE student_teacher_assignments (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id BIGINT NOT NULL UNIQUE,
    teacher_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_teacher_assignments_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE,
    CONSTRAINT fk_student_teacher_assignments_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_student_teacher_assignments_teacher ON student_teacher_assignments (teacher_id);

CREATE TABLE teacher_availability (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teacher_availability_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_teacher_availability_range CHECK (end_at > start_at)
);

CREATE INDEX idx_teacher_availability_teacher_time ON teacher_availability (teacher_id, start_at, end_at);

CREATE TABLE teacher_bookings (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    availability_id BIGINT NOT NULL,
    start_at TIMESTAMP NOT NULL,
    end_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'BOOKED',
    active_teacher_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'BOOKED' THEN teacher_id ELSE NULL END) ${generatedStored},
    active_slot_start TIMESTAMP GENERATED ALWAYS AS (CASE WHEN status = 'BOOKED' THEN start_at ELSE NULL END) ${generatedStored},
    active_student_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'BOOKED' THEN student_id ELSE NULL END) ${generatedStored},
    active_lesson_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'BOOKED' THEN lesson_id ELSE NULL END) ${generatedStored},
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teacher_bookings_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher_bookings_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher_bookings_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher_bookings_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher_bookings_availability FOREIGN KEY (availability_id) REFERENCES teacher_availability(id) ON DELETE CASCADE,
    CONSTRAINT chk_teacher_bookings_range CHECK (end_at > start_at)
);

CREATE UNIQUE INDEX idx_teacher_bookings_teacher_slot_booked ON teacher_bookings (active_teacher_id, active_slot_start);
CREATE UNIQUE INDEX idx_teacher_bookings_student_lesson_booked ON teacher_bookings (active_student_id, active_lesson_id);
CREATE INDEX idx_teacher_bookings_teacher_status ON teacher_bookings (teacher_id, status);
CREATE INDEX idx_teacher_bookings_student_status ON teacher_bookings (student_id, status);

CREATE TABLE teacher_reviews (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE,
    result VARCHAR(20) NOT NULL,
    comment TEXT,
    reviewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teacher_reviews_booking FOREIGN KEY (booking_id) REFERENCES teacher_bookings(id) ON DELETE CASCADE
);

CREATE TABLE teacher_compensation_configs (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL UNIQUE,
    amount_per_session NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teacher_compensation_configs_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_teacher_compensation_amount CHECK (amount_per_session >= 0)
);

CREATE TABLE teacher_earnings (
    id BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT NOT NULL,
    booking_id BIGINT NOT NULL UNIQUE,
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'EARNED',
    earned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_teacher_earnings_teacher FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_teacher_earnings_booking FOREIGN KEY (booking_id) REFERENCES teacher_bookings(id) ON DELETE CASCADE,
    CONSTRAINT chk_teacher_earnings_amount CHECK (amount >= 0)
);

CREATE INDEX idx_teacher_earnings_teacher ON teacher_earnings (teacher_id);
