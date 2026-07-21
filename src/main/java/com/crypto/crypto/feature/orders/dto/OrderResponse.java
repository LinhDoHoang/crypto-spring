package com.crypto.crypto.feature.orders.dto;

import com.crypto.crypto.entities.OrdersEntity;
import com.crypto.crypto.feature.orders.constant.CloseReasonEnum;
import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.OrderStatusEnum;
import com.crypto.crypto.feature.orders.constant.OrderTypeEnum;
import com.crypto.crypto.feature.orders.constant.SizingModeEnum;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        Long id,
        Long accountId,
        Long userId,
        String clientOrderId,
        SymbolEnum symbol,
        OrderSideEnum side,
        OrderTypeEnum orderType,
        SizingModeEnum sizingMode,
        BigDecimal quantity,
        BigDecimal entryPrice,
        Instant entryMarkTimestamp,
        BigDecimal notional,
        Integer leverage,
        BigDecimal initialMargin,
        BigDecimal maintenanceMarginRate,
        BigDecimal takeProfit,
        BigDecimal stopLoss,
        OrderStatusEnum status,
        CloseReasonEnum closeReason,
        BigDecimal closePrice,
        BigDecimal realizedPnl,
        BigDecimal tradingFee,
        BigDecimal clientMark,
        Instant clientTimestamp,
        Integer maxSlippageBps,
        Instant openedAt,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrderResponse from(OrdersEntity order) {
        return new OrderResponse(
                order.getId(), order.getAccountId(), order.getUserId(), order.getClientOrderId(),
                order.getSymbol(), order.getSide(), order.getOrderType(), order.getSizingMode(),
                order.getQuantity(), order.getEntryPrice(), order.getEntryMarkTimestamp(),
                order.getNotional(), order.getLeverage(), order.getInitialMargin(),
                order.getMaintenanceMarginRate(), order.getTakeProfit(), order.getStopLoss(),
                order.getStatus(), order.getCloseReason(), order.getClosePrice(), order.getRealizedPnl(),
                order.getTradingFee(), order.getClientMark(), order.getClientTimestamp(),
                order.getMaxSlippageBps(), order.getOpenedAt(), order.getClosedAt(),
                order.getCreatedAt(), order.getUpdatedAt()
        );
    }
}
