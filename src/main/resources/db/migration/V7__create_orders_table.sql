CREATE TYPE symbol_enum AS ENUM ('BTCUSDT', 'ETHUSDT', 'SOLUSDT');
CREATE TYPE side_enum AS ENUM ('BUY', 'SELL');
CREATE TYPE order_type_enum AS ENUM ('MARKET');
CREATE TYPE sizing_mode_enum AS ENUM ('UNITS', 'NOTIONAL');
CREATE TYPE orders_status_enum AS ENUM ('OPEN', 'CLOSED', 'LIQUIDATED');
CREATE TYPE close_reason_enum AS ENUM ('MANUAL', 'TP', 'SL', 'LIQUIDATION');

CREATE TABLE orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    client_order_id VARCHAR(100),
    symbol symbol_enum DEFAULT 'BTCUSDT',
    side side_enum,
    order_type order_type_enum DEFAULT 'MARKET',
    sizing_mode sizing_mode_enum,
    quantity NUMERIC(28, 12),
    entry_price NUMERIC(28, 12),
    entry_mark_timestamp TIMESTAMPTZ,
    notional NUMERIC(28, 12),
    leverage INTEGER,
    initial_margin NUMERIC(28, 12),
    maintenance_margin_rate NUMERIC(10, 8),
    take_profit NUMERIC(28, 12),
    stop_loss NUMERIC(28, 12),
    status orders_status_enum DEFAULT 'OPEN',
    close_reason close_reason_enum,
    close_price NUMERIC(28,12),
    realized_pnl NUMERIC(28,8),
    trading_fee NUMERIC(28,8),
    client_mark NUMERIC(28,12),
    client_timestamp TIMESTAMPTZ,
    max_slippage_bps INTEGER,
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    version INTEGER
)