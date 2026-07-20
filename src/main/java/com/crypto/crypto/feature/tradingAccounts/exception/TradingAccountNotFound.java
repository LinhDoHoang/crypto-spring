package com.crypto.crypto.feature.tradingAccounts.exception;

public class TradingAccountNotFound extends RuntimeException {
    public TradingAccountNotFound(Long id) {
        super("Can not find trading account with id: " + id);
    }
}
