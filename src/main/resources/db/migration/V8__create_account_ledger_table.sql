CREATE TABLE account_ledgers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    order_id BIGINT,
    type VARCHAR(30),
    amount NUMERIC(28,8),
    balance_before NUMERIC(28,8),
    balance_after NUMERIC(28,8),
    description VARCHAR(500)
);