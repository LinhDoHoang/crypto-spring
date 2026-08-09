package com.crypto.crypto.feature.trades.dto;

import java.math.BigDecimal;

public record CandleResponse(
        long timestamp,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        int decimal
) {
}
