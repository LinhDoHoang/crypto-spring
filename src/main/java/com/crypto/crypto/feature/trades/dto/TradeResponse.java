package com.crypto.crypto.feature.trades.dto;

import com.crypto.crypto.entities.TradesEntity;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.trades.constant.TradeSourceEnum;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeResponse(
        Long id,
        Instant time,
        SymbolEnum asset,
        BigDecimal price,
        BigDecimal quantity,
        TradeSourceEnum source,
        Long sourceTradeId,
        Instant createdAt,
        Instant updatedAt
) {
    public static TradeResponse from(TradesEntity trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getTime(),
                trade.getAsset(),
                trade.getPrice(),
                trade.getQuantity(),
                trade.getSource(),
                trade.getSourceTradeId(),
                trade.getCreatedAt(),
                trade.getUpdatedAt()
        );
    }
}
