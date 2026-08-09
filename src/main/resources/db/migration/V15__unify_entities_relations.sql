ALTER TABLE orders
    ADD CONSTRAINT FK_trading_account_orders FOREIGN KEY (trading_account_id)
        REFERENCES trading_accounts (id);

ALTER TABLE account_ledgers
    ADD CONSTRAINT FK_order_account_ledgers FOREIGN KEY (order_id)
    REFERENCES orders (id);