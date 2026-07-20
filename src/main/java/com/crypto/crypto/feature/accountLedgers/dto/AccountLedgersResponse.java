package com.crypto.crypto.feature.accountLedgers.dto;

import com.crypto.crypto.entities.AccountLedgersEntity;

public record AccountLedgersResponse (
        Long id,
        Long account_id,
        Long order_id,
        String type,
        Float amount,
        Float balanceBefore,
        Float balanceAfter,
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
