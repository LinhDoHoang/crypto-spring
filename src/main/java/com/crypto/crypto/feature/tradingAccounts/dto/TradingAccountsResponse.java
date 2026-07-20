package com.crypto.crypto.feature.tradingAccounts.dto;

import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;

public record TradingAccountsResponse(
        Long id,
        Long userId,
        AccountTypeEnum accountType,
        String currency,
        Float balance,
        StatusEnum status,
        Integer defaultLeverage
) {
    public static TradingAccountsResponse from(TradingAccountsEntity tradingAccounts) {
        return new TradingAccountsResponse(
                tradingAccounts.getId(),
                tradingAccounts.getUserId(),
                tradingAccounts.getAccountType(),
                tradingAccounts.getCurrency(),
                tradingAccounts.getBalance(),
                tradingAccounts.getStatus(),
                tradingAccounts.getDefaultLeverage()
        );
    }
}
