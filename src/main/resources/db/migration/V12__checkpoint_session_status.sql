ALTER TABLE checkpoint_sessions
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE checkpoint_sessions
ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE checkpoint_sessions
ADD CONSTRAINT chk_checkpoint_sessions_status CHECK (status IN ('PENDING', 'COMPLETED', 'CANCELLED'));

CREATE INDEX idx_checkpoint_sessions_status_time ON checkpoint_sessions (status, scheduled_at);
