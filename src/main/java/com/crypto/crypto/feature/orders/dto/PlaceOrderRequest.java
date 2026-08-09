package com.crypto.crypto.feature.orders.dto;

import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.SizingModeEnum;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PlaceOrderRequest {
    @NotNull
    private SymbolEnum symbol;

    @NotNull
    private OrderSideEnum side;

    @NotNull
    private SizingModeEnum mode;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal qtyUnits;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal notionalUsd;

    @NotNull
    @Min(1)
    @Max(100)
    private Integer leverage;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal tp;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal sl;

    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal clientMark;

    private Long clientTs;

    @Min(0)
    @Max(100)
    private Integer maxSlippageBps;

    @Size(max = 100)
    private String clientOrderId;
}
