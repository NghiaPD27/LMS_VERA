CREATE TABLE lesson_videos (
    id BIGSERIAL PRIMARY KEY,
    lesson_id BIGINT NOT NULL UNIQUE,
    bunny_video_id VARCHAR(100) NOT NULL,
    library_id VARCHAR(100) NOT NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    thumbnail_url VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lesson_videos_lesson FOREIGN KEY (lesson_id) REFERENCES lessons(id) ON DELETE CASCADE,
    CONSTRAINT chk_lesson_videos_duration CHECK (duration_seconds >= 0)
);

CREATE INDEX idx_lesson_videos_lesson ON lesson_videos (lesson_id);
CREATE INDEX idx_lesson_videos_status ON lesson_videos (status);

CREATE TABLE video_progress (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL,
    lesson_video_id BIGINT NOT NULL,
    current_second INT NOT NULL DEFAULT 0,
    furthest_watched_second INT NOT NULL DEFAULT 0,
    watched_percentage INT NOT NULL DEFAULT 0,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_video_progress_student FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_video_progress_lesson_video FOREIGN KEY (lesson_video_id) REFERENCES lesson_videos(id) ON DELETE CASCADE,
    CONSTRAINT chk_video_progress_current_second CHECK (current_second >= 0),
    CONSTRAINT chk_video_progress_furthest_second CHECK (furthest_watched_second >= 0),
    CONSTRAINT chk_video_progress_watched_percentage CHECK (watched_percentage >= 0 AND watched_percentage <= 100)
);

CREATE UNIQUE INDEX idx_video_progress_student_lesson_video ON video_progress (student_id, lesson_video_id);
CREATE INDEX idx_video_progress_student ON video_progress (student_id);
CREATE INDEX idx_video_progress_lesson_video ON video_progress (lesson_video_id);
