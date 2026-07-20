package com.crypto.crypto.feature.tradingAccounts.dto;

import com.crypto.crypto.config.enumValidator.EnumPattern;
import com.crypto.crypto.constant.request.ModifiedDto;
import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.validation.annotation.Validated;

@Validated
@Getter
@Setter
public class CreateTradingAccountsDto extends ModifiedDto {
    @NotNull
    private Long userId;

    @EnumPattern(enumClass = AccountTypeEnum.class)
    private AccountTypeEnum accountType;
    private String currency;
    private Float balance;

    @EnumPattern(enumClass = StatusEnum.class)
    private StatusEnum status;
    private Integer defaultLeverage;
    private Integer version;
}
