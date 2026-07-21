package com.crypto.crypto.feature.orders.dto;

import com.crypto.crypto.feature.orders.constant.CloseReasonEnum;
import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.OrderStatusEnum;
import com.crypto.crypto.feature.orders.constant.OrderTypeEnum;
import com.crypto.crypto.feature.orders.constant.SizingModeEnum;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class UpdateOrderDto {
    @Positive
    private Long accountId;

    @Positive
    private Long userId;

    @Size(max = 100)
    private String clientOrderId;

    private SymbolEnum symbol;
    private OrderSideEnum side;
    private OrderTypeEnum orderType;
    private SizingModeEnum sizingMode;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal entryPrice;

    private Instant entryMarkTimestamp;

    @DecimalMin("0.0")
    private BigDecimal notional;

    @Min(1)
    @Max(100)
    private Integer leverage;

    @DecimalMin("0.0")
    private BigDecimal initialMargin;

    @DecimalMin("0.0")
    private BigDecimal maintenanceMarginRate;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal takeProfit;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal stopLoss;

    private OrderStatusEnum status;
    private CloseReasonEnum closeReason;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal closePrice;

    private BigDecimal realizedPnl;

    @DecimalMin("0.0")
    private BigDecimal tradingFee;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal clientMark;

    private Instant clientTimestamp;

    @Min(0)
    @Max(100)
    private Integer maxSlippageBps;

    private Instant openedAt;
    private Instant closedAt;
}
