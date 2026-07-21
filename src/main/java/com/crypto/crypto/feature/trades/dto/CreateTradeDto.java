package com.crypto.crypto.feature.trades.dto;

import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.trades.constant.TradeSourceEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
public class CreateTradeDto {
    @NotNull
    private Instant time;

    @NotNull
    private SymbolEnum asset;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal price;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;

    @NotNull
    private TradeSourceEnum source;

    @Positive
    private Long sourceTradeId;
}
