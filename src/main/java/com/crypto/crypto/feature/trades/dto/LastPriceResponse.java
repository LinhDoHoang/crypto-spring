package com.crypto.crypto.feature.trades.dto;

import com.crypto.crypto.feature.orders.constant.SymbolEnum;

import java.math.BigDecimal;
import java.time.Instant;

public record LastPriceResponse(
        SymbolEnum asset,
        Instant ts,
        BigDecimal price,
        BigDecimal quantity
) {
}
