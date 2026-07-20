ALTER TABLE users
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN updated_by BIGINT,
    ADD COLUMN deleted_by BIGINT;