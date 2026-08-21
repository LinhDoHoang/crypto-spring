ALTER TABLE trading_accounts
    ADD CONSTRAINT FK_trading_accounts_users FOREIGN KEY (user_id)
        REFERENCES users (id)
