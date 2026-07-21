package com.crypto.crypto.feature.orders.exception;

import com.crypto.crypto.config.ResourceNotFoundException;

public class OrderNotFoundException extends ResourceNotFoundException {
    public OrderNotFoundException(Long id) {
        super("Order", id);
    }
}
