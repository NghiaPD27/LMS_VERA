CREATE TABLE programs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT
);

CREATE TABLE lessons (
    id BIGSERIAL PRIMARY KEY,
    program_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    lesson_number INT NOT NULL,
    content TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    active_lesson_number INT GENERATED ALWAYS AS (CASE WHEN status <> 'ARCHIVED' THEN lesson_number ELSE NULL END) ${generatedStored},
    CONSTRAINT fk_lessons_program FOREIGN KEY (program_id) REFERENCES programs(id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX idx_lessons_program_number_active ON lessons (program_id, active_lesson_number);

CREATE TABLE enrollments (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    active_student_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' THEN student_id ELSE NULL END) ${generatedStored},
    CONSTRAINT fk_enrollments_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_enrollments_program FOREIGN KEY (program_id) REFERENCES programs(id) ON DELETE CASCADE
);

-- Index to enforce active enrollment per student
CREATE UNIQUE INDEX idx_enrollments_student_active ON enrollments (active_student_id);

CREATE TABLE student_lesson_progress (
    student_id BIGINT NOT NULL,
    lesson_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'LOCKED',
    PRIMARY KEY (student_id, lesson_id),
    CONSTRAINT fk_progress_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE
);

-- Seed default data
INSERT INTO programs (name, description) VALUES ('English A1', 'Elementary English');
INSERT INTO programs (name, description) VALUES ('English A2', 'Pre-intermediate English');

INSERT INTO lessons (program_id, name, lesson_number, content, status) VALUES
((SELECT id FROM programs WHERE name = 'English A1'), 'Lesson 1', 1, 'Greetings & Introductions', 'PUBLISHED'),
((SELECT id FROM programs WHERE name = 'English A1'), 'Lesson 2', 2, 'Basic Vocabulary', 'PUBLISHED'),
((SELECT id FROM programs WHERE name = 'English A2'), 'Lesson 1', 1, 'Describing People', 'PUBLISHED'),
((SELECT id FROM programs WHERE name = 'English A2'), 'Lesson 2', 2, 'Daily Routines', 'PUBLISHED');
