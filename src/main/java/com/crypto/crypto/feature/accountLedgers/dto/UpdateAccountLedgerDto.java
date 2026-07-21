package com.crypto.crypto.feature.accountLedgers.dto;

import com.crypto.crypto.feature.accountLedgers.constant.LedgerTypeEnum;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateAccountLedgerDto {
    @Positive
    private Long accountId;

    @Positive
    private Long orderId;

    private LedgerTypeEnum type;

    private BigDecimal amount;

    @DecimalMin("0.0")
    private BigDecimal balanceBefore;

    @DecimalMin("0.0")
    private BigDecimal balanceAfter;

    @Size(max = 500)
    private String description;
}
