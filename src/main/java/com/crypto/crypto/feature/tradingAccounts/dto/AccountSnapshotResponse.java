package com.crypto.crypto.feature.tradingAccounts.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AccountSnapshotResponse(
        Long accountId,
        BigDecimal balance,
        BigDecimal equity,
        BigDecimal freeMargin,
        BigDecimal usedMargin,
        BigDecimal upnl,
        BigDecimal maintenance,
        BigDecimal marginLevel,
        Integer leverage,
        Instant priceAsOf
) {
}
