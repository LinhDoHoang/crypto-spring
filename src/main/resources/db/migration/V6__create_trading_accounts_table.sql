CREATE TYPE account_type_enum AS ENUM ('DEMO', 'ADMIN');
CREATE TYPE status_enum AS ENUM ('ACTIVE', 'LOCKED', 'CLOSED');

CREATE TABLE trading_accounts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_type account_type_enum DEFAULT 'DEMO',
    currency VARCHAR(20),
    balance NUMERIC(24, 8),
    status status_enum DEFAULT 'ACTIVE',
    default_leverage INTEGER,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_by BIGINT,
    updated_at TIMESTAMPTZ,
    updated_by BIGINT,
    deleted_at TIMESTAMPTZ,
    deleted_by BIGINT,
    version INTEGER NOT NULL DEFAULT 0
);
