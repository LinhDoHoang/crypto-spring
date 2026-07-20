package com.crypto.crypto.feature.accountLedgers;

import com.crypto.crypto.entities.AccountLedgersEntity;
import com.crypto.crypto.feature.accountLedgers.dto.AccountLedgersResponse;
import com.crypto.crypto.feature.accountLedgers.dto.CreateAccountLedgerDto;
import com.crypto.crypto.util.GlobalUtils;
import org.springframework.stereotype.Service;

@Service
public class AccountLedgersService {
    private final AccountLedgersRepository accountLedgersRepository;

    AccountLedgersService(AccountLedgersRepository accountLedgersRepository) {
        this.accountLedgersRepository = accountLedgersRepository;
    }

    public AccountLedgersResponse create(CreateAccountLedgerDto createAccountLedgerDto) {
        var builder = AccountLedgersEntity.builder()
                .accountId(createAccountLedgerDto.getAccountId());

        if (GlobalUtils.isNull(createAccountLedgerDto.getOrderId())) {
            
        }
    }
}
