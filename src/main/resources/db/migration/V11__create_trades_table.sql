CREATE TABLE trades (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    time TIMESTAMPTZ NOT NULL,
    asset TEXT NOT NULL,
    price NUMERIC(28, 12) NOT NULL,
    quantity NUMERIC(28, 12) NOT NULL,
    source VARCHAR(20) NOT NULL,
    source_trade_id BIGINT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at TIMESTAMPTZ,
    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    CONSTRAINT chk_trades_price_positive CHECK (price > 0),
    CONSTRAINT chk_trades_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_trades_source_trade_id_positive CHECK (source_trade_id IS NULL OR source_trade_id > 0)
);

CREATE UNIQUE INDEX ux_trades_active_source_trade
    ON trades (asset, source_trade_id)
    WHERE source_trade_id IS NOT NULL AND deleted_at IS NULL;

CREATE INDEX ix_trades_asset_time
    ON trades (asset, time DESC)
    WHERE deleted_at IS NULL;
