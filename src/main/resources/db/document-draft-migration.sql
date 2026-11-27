BEGIN;

ALTER TABLE documents ADD COLUMN rag_attempt_id UUID;

ALTER TABLE document_rag_requests
    ALTER COLUMN document_id DROP NOT NULL,
    ADD COLUMN deleted_document_id UUID,
    ADD COLUMN deleted_document_title VARCHAR(255),
    ADD CONSTRAINT chk_document_rag_request_reference CHECK (
        (document_id IS NOT NULL AND deleted_document_id IS NULL AND deleted_document_title IS NULL)
        OR (document_id IS NULL AND deleted_document_id IS NOT NULL
            AND deleted_document_title IS NOT NULL AND status = 'REJECTED')
    );

CREATE TABLE document_storage_cleanup (
    storage_path TEXT PRIMARY KEY,
    retry_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMIT;
