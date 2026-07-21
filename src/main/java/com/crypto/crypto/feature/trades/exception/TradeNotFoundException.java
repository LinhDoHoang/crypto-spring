package com.crypto.crypto.feature.trades.exception;

import com.crypto.crypto.config.ResourceNotFoundException;

public class TradeNotFoundException extends ResourceNotFoundException {
    public TradeNotFoundException(Long id) {
        super("Trade", id);
    }
}
