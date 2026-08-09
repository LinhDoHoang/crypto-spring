package com.crypto.crypto.feature.orders.dto;

import com.crypto.crypto.entities.OrdersEntity;
import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.OrderStatusEnum;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;

import java.math.BigDecimal;
import java.time.Instant;

public record PositionResponse(
        Long id,
        SymbolEnum symbol,
        OrderSideEnum side,
        BigDecimal volume,
        BigDecimal units,
        BigDecimal entry,
        BigDecimal entryPrice,
        BigDecimal mark,
        Integer leverage,
        BigDecimal requiredMargin,
        OrderStatusEnum status,
        BigDecimal closePrice,
        Instant closedAt,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal takeProfit,
        BigDecimal stopLoss,
        Instant createdAt
) {
    public static PositionResponse from(
            OrdersEntity order,
            BigDecimal mark,
            BigDecimal unrealizedPnl
    ) {
        return new PositionResponse(
                order.getId(),
                order.getSymbol(),
                order.getSide(),
                order.getQuantity(),
                order.getQuantity(),
                order.getEntryPrice(),
                order.getEntryPrice(),
                mark,
                order.getLeverage(),
                order.getInitialMargin(),
                order.getStatus(),
                order.getClosePrice(),
                order.getClosedAt(),
                order.getRealizedPnl(),
                unrealizedPnl,
                order.getTakeProfit(),
                order.getStopLoss(),
                order.getCreatedAt()
        );
    }
}
