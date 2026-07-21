package com.crypto.crypto.feature.tradingAccounts.exception;

import com.crypto.crypto.config.ResourceNotFoundException;

public class TradingAccountNotFound extends ResourceNotFoundException {
    public TradingAccountNotFound(Long id) {
        super("Trading account", id);
    }
}
