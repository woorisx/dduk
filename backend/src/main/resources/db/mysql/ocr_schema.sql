CREATE TABLE IF NOT EXISTS ocr_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    processing_status VARCHAR(30) NOT NULL,
    review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    link_status VARCHAR(30) NOT NULL DEFAULT 'UNLINKED',
    linked_domain_type VARCHAR(30) NULL,
    linked_domain_id BIGINT NULL,
    extracted_vendor VARCHAR(100) NULL,
    extracted_date DATE NULL,
    extracted_amount DECIMAL(15,2) NULL,
    raw_ocr_result LONGTEXT NULL,
    reviewed_result LONGTEXT NULL,
    error_code VARCHAR(100) NULL,
    error_message VARCHAR(500) NULL,
    created_by_member_id BIGINT NULL,
    reviewed_by_member_id BIGINT NULL,
    reviewed_at DATETIME NULL,
    linked_by_member_id BIGINT NULL,
    linked_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ocr_documents_stored_filename (stored_filename),
    KEY idx_ocr_documents_document_type (document_type),
    KEY idx_ocr_documents_processing_status (processing_status),
    KEY idx_ocr_documents_review_status (review_status),
    KEY idx_ocr_documents_link_status (link_status),
    KEY idx_ocr_documents_linked_target (linked_domain_type, linked_domain_id),
    KEY idx_ocr_documents_created_by_member (created_by_member_id),
    KEY idx_ocr_documents_reviewed_by_member (reviewed_by_member_id),
    KEY idx_ocr_documents_linked_by_member (linked_by_member_id),
    CONSTRAINT fk_ocr_documents_created_by_member
        FOREIGN KEY (created_by_member_id) REFERENCES members (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT fk_ocr_documents_reviewed_by_member
        FOREIGN KEY (reviewed_by_member_id) REFERENCES members (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE,
    CONSTRAINT fk_ocr_documents_linked_by_member
        FOREIGN KEY (linked_by_member_id) REFERENCES members (id)
        ON DELETE SET NULL
        ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE ocr_documents
    ADD COLUMN IF NOT EXISTS original_filename VARCHAR(255) NOT NULL,
    ADD COLUMN IF NOT EXISTS stored_filename VARCHAR(255) NOT NULL,
    ADD COLUMN IF NOT EXISTS storage_path VARCHAR(500) NOT NULL,
    ADD COLUMN IF NOT EXISTS mime_type VARCHAR(100) NOT NULL,
    ADD COLUMN IF NOT EXISTS file_size BIGINT NOT NULL,
    ADD COLUMN IF NOT EXISTS document_type VARCHAR(30) NOT NULL,
    ADD COLUMN IF NOT EXISTS processing_status VARCHAR(30) NOT NULL,
    ADD COLUMN IF NOT EXISTS review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS link_status VARCHAR(30) NOT NULL DEFAULT 'UNLINKED',
    ADD COLUMN IF NOT EXISTS linked_domain_type VARCHAR(30) NULL,
    ADD COLUMN IF NOT EXISTS linked_domain_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS extracted_vendor VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS extracted_date DATE NULL,
    ADD COLUMN IF NOT EXISTS extracted_amount DECIMAL(15,2) NULL,
    ADD COLUMN IF NOT EXISTS raw_ocr_result LONGTEXT NULL,
    ADD COLUMN IF NOT EXISTS reviewed_result LONGTEXT NULL,
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(100) NULL,
    ADD COLUMN IF NOT EXISTS error_message VARCHAR(500) NULL,
    ADD COLUMN IF NOT EXISTS created_by_member_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS reviewed_by_member_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS reviewed_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS linked_by_member_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS linked_at DATETIME NULL,
    ADD COLUMN IF NOT EXISTS created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE ocr_documents
    MODIFY COLUMN original_filename VARCHAR(255) NOT NULL,
    MODIFY COLUMN stored_filename VARCHAR(255) NOT NULL,
    MODIFY COLUMN storage_path VARCHAR(500) NOT NULL,
    MODIFY COLUMN mime_type VARCHAR(100) NOT NULL,
    MODIFY COLUMN file_size BIGINT NOT NULL,
    MODIFY COLUMN document_type VARCHAR(30) NOT NULL,
    MODIFY COLUMN processing_status VARCHAR(30) NOT NULL,
    MODIFY COLUMN review_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    MODIFY COLUMN link_status VARCHAR(30) NOT NULL DEFAULT 'UNLINKED',
    MODIFY COLUMN linked_domain_type VARCHAR(30) NULL,
    MODIFY COLUMN linked_domain_id BIGINT NULL,
    MODIFY COLUMN extracted_vendor VARCHAR(100) NULL,
    MODIFY COLUMN extracted_date DATE NULL,
    MODIFY COLUMN extracted_amount DECIMAL(15,2) NULL,
    MODIFY COLUMN raw_ocr_result LONGTEXT NULL,
    MODIFY COLUMN reviewed_result LONGTEXT NULL,
    MODIFY COLUMN error_code VARCHAR(100) NULL,
    MODIFY COLUMN error_message VARCHAR(500) NULL,
    MODIFY COLUMN created_by_member_id BIGINT NULL,
    MODIFY COLUMN reviewed_by_member_id BIGINT NULL,
    MODIFY COLUMN reviewed_at DATETIME NULL,
    MODIFY COLUMN linked_by_member_id BIGINT NULL,
    MODIFY COLUMN linked_at DATETIME NULL,
    MODIFY COLUMN created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND constraint_type = 'PRIMARY KEY'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD PRIMARY KEY (id)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'uk_ocr_documents_stored_filename'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD UNIQUE KEY uk_ocr_documents_stored_filename (stored_filename)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'idx_ocr_documents_document_type'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD KEY idx_ocr_documents_document_type (document_type)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'idx_ocr_documents_processing_status'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD KEY idx_ocr_documents_processing_status (processing_status)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'idx_ocr_documents_review_status'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD KEY idx_ocr_documents_review_status (review_status)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'idx_ocr_documents_link_status'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD KEY idx_ocr_documents_link_status (link_status)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'idx_ocr_documents_linked_target'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD KEY idx_ocr_documents_linked_target (linked_domain_type, linked_domain_id)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'idx_ocr_documents_created_by_member'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD KEY idx_ocr_documents_created_by_member (created_by_member_id)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'idx_ocr_documents_reviewed_by_member'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD KEY idx_ocr_documents_reviewed_by_member (reviewed_by_member_id)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND index_name = 'idx_ocr_documents_linked_by_member'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD KEY idx_ocr_documents_linked_by_member (linked_by_member_id)'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND constraint_name = 'fk_ocr_documents_created_by_member'
              AND constraint_type = 'FOREIGN KEY'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD CONSTRAINT fk_ocr_documents_created_by_member FOREIGN KEY (created_by_member_id) REFERENCES members (id) ON DELETE SET NULL ON UPDATE CASCADE'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND constraint_name = 'fk_ocr_documents_reviewed_by_member'
              AND constraint_type = 'FOREIGN KEY'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD CONSTRAINT fk_ocr_documents_reviewed_by_member FOREIGN KEY (reviewed_by_member_id) REFERENCES members (id) ON DELETE SET NULL ON UPDATE CASCADE'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.table_constraints
            WHERE table_schema = DATABASE()
              AND table_name = 'ocr_documents'
              AND constraint_name = 'fk_ocr_documents_linked_by_member'
              AND constraint_type = 'FOREIGN KEY'
        ),
        'SELECT 1',
        'ALTER TABLE ocr_documents ADD CONSTRAINT fk_ocr_documents_linked_by_member FOREIGN KEY (linked_by_member_id) REFERENCES members (id) ON DELETE SET NULL ON UPDATE CASCADE'
    )
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
