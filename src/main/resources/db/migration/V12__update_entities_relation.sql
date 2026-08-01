ALTER TABLE trading_accounts
    ADD COLUMN user_id BIGINT NOT NULL;

ALTER TABLE trading_accounts
    ADD CONSTRAINT FK_trading_accounts_users FOREIGN KEY (user_id)
        REFERENCES users (id)
