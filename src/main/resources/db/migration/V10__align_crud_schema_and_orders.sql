ALTER TABLE orders
    ADD COLUMN created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ADD COLUMN updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    ADD COLUMN deleted_at TIMESTAMPTZ,
    ADD COLUMN created_by BIGINT,
    ADD COLUMN updated_by BIGINT,
    ADD COLUMN deleted_by BIGINT;

UPDATE trading_accounts SET account_type = 'DEMO' WHERE account_type IS NULL;
UPDATE trading_accounts SET currency = 'USDT' WHERE currency IS NULL;
UPDATE trading_accounts SET balance = 0 WHERE balance IS NULL;
UPDATE trading_accounts SET status = 'ACTIVE' WHERE status IS NULL;
UPDATE trading_accounts SET default_leverage = 1 WHERE default_leverage IS NULL;

ALTER TABLE trading_accounts
    ALTER COLUMN account_type SET NOT NULL,
    ALTER COLUMN currency SET DEFAULT 'USDT',
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN balance SET DEFAULT 0,
    ALTER COLUMN balance SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN default_leverage SET DEFAULT 1,
    ALTER COLUMN default_leverage SET NOT NULL,
    ADD CONSTRAINT chk_trading_accounts_balance_non_negative CHECK (balance >= 0) NOT VALID,
    ADD CONSTRAINT chk_trading_accounts_leverage_range CHECK (default_leverage BETWEEN 1 AND 100) NOT VALID;

UPDATE orders SET version = 0 WHERE version IS NULL;
UPDATE orders SET trading_fee = 0 WHERE trading_fee IS NULL;
UPDATE orders SET symbol = 'BTCUSDT' WHERE symbol IS NULL;
UPDATE orders SET order_type = 'MARKET' WHERE order_type IS NULL;
UPDATE orders SET status = 'OPEN' WHERE status IS NULL;

ALTER TABLE orders
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL,
    ALTER COLUMN trading_fee SET DEFAULT 0,
    ALTER COLUMN trading_fee SET NOT NULL,
    ALTER COLUMN symbol SET NOT NULL,
    ALTER COLUMN order_type SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_quantity_positive CHECK (quantity > 0) NOT VALID,
    ADD CONSTRAINT chk_orders_entry_price_positive CHECK (entry_price > 0) NOT VALID,
    ADD CONSTRAINT chk_orders_leverage_range CHECK (leverage BETWEEN 1 AND 100) NOT VALID,
    ADD CONSTRAINT chk_orders_notional_non_negative CHECK (notional >= 0) NOT VALID,
    ADD CONSTRAINT chk_orders_initial_margin_non_negative CHECK (initial_margin >= 0) NOT VALID,
    ADD CONSTRAINT chk_orders_maintenance_margin_non_negative CHECK (maintenance_margin_rate >= 0) NOT VALID,
    ADD CONSTRAINT chk_orders_trading_fee_non_negative CHECK (trading_fee >= 0) NOT VALID;

CREATE UNIQUE INDEX ux_users_active_email
    ON users (LOWER(email))
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_trading_accounts_active_user_type
    ON trading_accounts (user_id, account_type)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX ux_orders_active_client_order
    ON orders (account_id, client_order_id)
    WHERE client_order_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_orders_account_status_opened
    ON orders (account_id, status, opened_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_orders_symbol_status
    ON orders (symbol, status)
    WHERE deleted_at IS NULL;
