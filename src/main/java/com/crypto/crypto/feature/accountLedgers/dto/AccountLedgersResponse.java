package com.crypto.crypto.feature.accountLedgers.dto;

import com.crypto.crypto.entities.AccountLedgersEntity;
import com.crypto.crypto.feature.accountLedgers.constant.LedgerTypeEnum;

import java.math.BigDecimal;

public record AccountLedgersResponse (
        Long id,
        Long account_id,
        Long order_id,
        LedgerTypeEnum type,
        BigDecimal amount,
        BigDecimal balanceBefore,
        BigDecimal balanceAfter,
        String description
) {
    public static AccountLedgersResponse from(AccountLedgersEntity accountLedgersEntity) {
        return new AccountLedgersResponse(
                accountLedgersEntity.getId(),
                accountLedgersEntity.getAccountId(),
                accountLedgersEntity.getOrderId(),
                accountLedgersEntity.getType(),
                accountLedgersEntity.getAmount(),
                accountLedgersEntity.getBalanceBefore(),
                accountLedgersEntity.getBalanceAfter(),
                accountLedgersEntity.getDescription()
        );
    }
}
