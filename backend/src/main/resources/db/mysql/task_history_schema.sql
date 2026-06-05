CREATE TABLE IF NOT EXISTS task_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(100) NOT NULL,
    task_type VARCHAR(30) NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    request_payload LONGTEXT NULL,
    response_payload LONGTEXT NULL,
    error_message VARCHAR(500) NULL,
    requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    callback_received_at DATETIME NULL,
    completed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_history_task_id (task_id),
    KEY idx_task_history_type_status (task_type, status),
    KEY idx_task_history_requested_at (requested_at),
    KEY idx_task_history_completed_at (completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE task_history
    ADD COLUMN IF NOT EXISTS request_payload LONGTEXT NULL,
    ADD COLUMN IF NOT EXISTS response_payload LONGTEXT NULL,
    ADD COLUMN IF NOT EXISTS error_message VARCHAR(500) NULL;

ALTER TABLE task_history
    MODIFY COLUMN request_payload LONGTEXT NULL,
    MODIFY COLUMN response_payload LONGTEXT NULL,
    MODIFY COLUMN error_message VARCHAR(500) NULL;
