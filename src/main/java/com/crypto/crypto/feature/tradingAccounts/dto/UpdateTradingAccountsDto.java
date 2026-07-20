package com.crypto.crypto.feature.tradingAccounts.dto;

import com.crypto.crypto.config.enumValidator.EnumPattern;
import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

@Setter
@Getter
@Validated
public class UpdateTradingAccountsDto {
    private Long userId;

    private AccountTypeEnum accountType;
    private String currency;
    private Float balance;
    private StatusEnum status;
    private Integer defaultLeverage;
}
