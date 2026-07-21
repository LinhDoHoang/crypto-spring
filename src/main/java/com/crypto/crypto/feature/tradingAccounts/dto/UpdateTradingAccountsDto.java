package com.crypto.crypto.feature.tradingAccounts.dto;

import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
public class UpdateTradingAccountsDto {
    @Positive
    private Long userId;

    private AccountTypeEnum accountType;

    @Size(max = 20)
    private String currency;

    @DecimalMin("0.0")
    private BigDecimal balance;

    private StatusEnum status;

    @Min(1)
    @Max(100)
    private Integer defaultLeverage;
}
