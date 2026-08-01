ALTER TABLE orders
    ADD CONSTRAINT FK_user_orders FOREIGN KEY (user_id)
        REFERENCES users (id)

