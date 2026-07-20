CREATE TABLE quizzes (
    id BIGSERIAL PRIMARY KEY,
    lesson_id BIGINT NOT NULL UNIQUE,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quizzes_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);

CREATE TABLE quiz_questions (
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    question_text TEXT NOT NULL,
    position INT NOT NULL,
    CONSTRAINT fk_quiz_questions_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_questions_position CHECK (position > 0)
);

CREATE UNIQUE INDEX idx_quiz_questions_quiz_position ON quiz_questions (quiz_id, position);

CREATE TABLE quiz_options (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL,
    option_text TEXT NOT NULL,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    position INT NOT NULL,
    CONSTRAINT fk_quiz_options_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_options_position CHECK (position > 0)
);

CREATE UNIQUE INDEX idx_quiz_options_question_position ON quiz_options (question_id, position);

CREATE TABLE quiz_attempts (
    id BIGSERIAL PRIMARY KEY,
    quiz_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    correct_count INT,
    total_questions INT,
    score_percent INT,
    attempt_number INT NOT NULL,
    submitted BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP,
    CONSTRAINT fk_quiz_attempts_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_attempts_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_quiz_attempts_attempt_number CHECK (attempt_number > 0),
    CONSTRAINT chk_quiz_attempts_score_percent CHECK (score_percent IS NULL OR (score_percent >= 0 AND score_percent <= 100))
);

CREATE INDEX idx_quiz_attempts_quiz_student ON quiz_attempts (quiz_id, student_id);
CREATE INDEX idx_quiz_attempts_student ON quiz_attempts (student_id);

CREATE TABLE quiz_answers (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    selected_option_id BIGINT,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_quiz_answers_attempt FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_answers_question FOREIGN KEY (question_id) REFERENCES quiz_questions(id) ON DELETE CASCADE,
    CONSTRAINT fk_quiz_answers_selected_option FOREIGN KEY (selected_option_id) REFERENCES quiz_options(id) ON DELETE SET NULL
);

CREATE UNIQUE INDEX idx_quiz_answers_attempt_question ON quiz_answers (attempt_id, question_id);
