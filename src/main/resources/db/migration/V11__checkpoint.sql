CREATE TABLE checkpoints (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL,
    block_number INTEGER NOT NULL,
    start_lesson_number INTEGER NOT NULL,
    gate_lesson_number INTEGER NOT NULL,
    next_lesson_number INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_checkpoints_program FOREIGN KEY (program_id) REFERENCES programs(id) ON DELETE CASCADE,
    CONSTRAINT uk_checkpoints_program_block UNIQUE (program_id, block_number),
    CONSTRAINT chk_checkpoints_block_number CHECK (block_number BETWEEN 1 AND 4),
    CONSTRAINT chk_checkpoints_lesson_numbers CHECK (
        start_lesson_number > 0
        AND gate_lesson_number >= start_lesson_number
        AND next_lesson_number > gate_lesson_number
    )
);

CREATE INDEX idx_checkpoints_program_gate ON checkpoints (program_id, gate_lesson_number);

CREATE TABLE checkpoint_sessions (
    id BIGSERIAL PRIMARY KEY,
    checkpoint_id BIGINT NOT NULL,
    evaluator_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    meet_link VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_checkpoint_sessions_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id) ON DELETE CASCADE,
    CONSTRAINT fk_checkpoint_sessions_evaluator FOREIGN KEY (evaluator_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_checkpoint_sessions_evaluator ON checkpoint_sessions (evaluator_id, scheduled_at);

CREATE TABLE checkpoint_participants (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_checkpoint_participants_session FOREIGN KEY (session_id) REFERENCES checkpoint_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_checkpoint_participants_enrollment FOREIGN KEY (enrollment_id) REFERENCES enrollments(id) ON DELETE CASCADE,
    CONSTRAINT fk_checkpoint_participants_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_checkpoint_participants_session_enrollment UNIQUE (session_id, enrollment_id)
);

CREATE INDEX idx_checkpoint_participants_student ON checkpoint_participants (student_id);
CREATE INDEX idx_checkpoint_participants_enrollment ON checkpoint_participants (enrollment_id);

CREATE TABLE checkpoint_results (
    id BIGSERIAL PRIMARY KEY,
    participant_id BIGINT NOT NULL UNIQUE,
    evaluator_id BIGINT NOT NULL,
    result VARCHAR(20) NOT NULL,
    comment TEXT,
    evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_checkpoint_results_participant FOREIGN KEY (participant_id) REFERENCES checkpoint_participants(id) ON DELETE CASCADE,
    CONSTRAINT fk_checkpoint_results_evaluator FOREIGN KEY (evaluator_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_checkpoint_results_result CHECK (result IN ('PASS', 'NOT_PASS'))
);

CREATE INDEX idx_checkpoint_results_evaluator ON checkpoint_results (evaluator_id, evaluated_at);
