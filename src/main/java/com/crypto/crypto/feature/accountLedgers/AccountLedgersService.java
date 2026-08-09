package com.crypto.crypto.feature.accountLedgers;

import com.crypto.crypto.entities.AccountLedgersEntity;
import com.crypto.crypto.config.ResourceNotFoundException;
import com.crypto.crypto.feature.accountLedgers.dto.AccountLedgersResponse;
import com.crypto.crypto.feature.accountLedgers.dto.CreateAccountLedgerDto;
import com.crypto.crypto.feature.accountLedgers.dto.UpdateAccountLedgerDto;
import com.crypto.crypto.feature.accountLedgers.exception.AccountLedgerNotfound;
import com.crypto.crypto.feature.tradingAccounts.TradingAccountRepository;
import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AccountLedgersService {
    private final AccountLedgersRepository accountLedgersRepository;
    private final TradingAccountRepository tradingAccountRepository;

    AccountLedgersService(
            AccountLedgersRepository accountLedgersRepository,
            TradingAccountRepository tradingAccountRepository
    ) {
        this.accountLedgersRepository = accountLedgersRepository;
        this.tradingAccountRepository = tradingAccountRepository;
    }

    @Transactional
    public AccountLedgersResponse create(CreateAccountLedgerDto createAccountLedgerDto) {
        var builder = AccountLedgersEntity.builder()
                .accountId(createAccountLedgerDto.getAccountId());

        if (createAccountLedgerDto.getOrderId() != null) {
            builder.orderId(createAccountLedgerDto.getOrderId());
        }

        if (createAccountLedgerDto.getType() != null) {
            builder.type(createAccountLedgerDto.getType());
        }

        if (createAccountLedgerDto.getAmount() != null) {
            builder.amount(createAccountLedgerDto.getAmount());
        }

        if (createAccountLedgerDto.getBalanceBefore() != null) {
            builder.balanceBefore(createAccountLedgerDto.getBalanceBefore());
        }

        if (createAccountLedgerDto.getBalanceAfter() != null) {
            builder.balanceAfter(createAccountLedgerDto.getBalanceAfter());
        }

        if (createAccountLedgerDto.getDescription() != null) {
            builder.description(createAccountLedgerDto.getDescription());
        }

        AccountLedgersEntity accountLedgers = builder.build();
        this.accountLedgersRepository.save(accountLedgers);
        return AccountLedgersResponse.from(accountLedgers);
    }

    @Transactional(readOnly = true)
    public AccountLedgersResponse getOne(Long id) {
        AccountLedgersEntity accountLedgers = this.accountLedgersRepository.findById(id)
                .orElseThrow(() -> new AccountLedgerNotfound(id));

        return AccountLedgersResponse.from(accountLedgers);
    }

    @Transactional(readOnly = true)
    public List<AccountLedgersResponse> getAll() {
        List<AccountLedgersEntity> accountLedgersEntities = this.accountLedgersRepository
                .findAll();

        return accountLedgersEntities.stream()
                .map(AccountLedgersResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountLedgersResponse> getForUser(Long userId) {
        Long accountId = tradingAccountRepository
                .findByUserIdAndAccountTypeAndStatus(
                        userId,
                        AccountTypeEnum.DEMO,
                        StatusEnum.ACTIVE
                )
                .map(account -> account.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Trading account",
                        userId
                ));

        return accountLedgersRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(AccountLedgersResponse::from)
                .toList();
    }

    @Transactional
    public AccountLedgersResponse update(Long id, UpdateAccountLedgerDto updateAccountLedgerDto) {
        AccountLedgersEntity accountLedgers = this.accountLedgersRepository.findById(id)
                .orElseThrow(() -> new AccountLedgerNotfound(id));

        if (updateAccountLedgerDto.getAccountId() != null) {
            accountLedgers.setAccountId(updateAccountLedgerDto.getAccountId());
        }

        if (updateAccountLedgerDto.getOrderId() != null) {
            accountLedgers.setOrderId(updateAccountLedgerDto.getOrderId());
        }

        if (updateAccountLedgerDto.getType() != null) {
            accountLedgers.setType(updateAccountLedgerDto.getType());
        }

        if (updateAccountLedgerDto.getAmount() != null) {
            accountLedgers.setAmount(updateAccountLedgerDto.getAmount());
        }

        if (updateAccountLedgerDto.getBalanceBefore() != null) {
            accountLedgers.setBalanceBefore(updateAccountLedgerDto.getBalanceBefore());
        }

        if (updateAccountLedgerDto.getBalanceAfter() != null) {
            accountLedgers.setBalanceAfter(updateAccountLedgerDto.getBalanceAfter());
        }

        if (updateAccountLedgerDto.getDescription() != null) {
            accountLedgers.setDescription(updateAccountLedgerDto.getDescription());
        }

        return AccountLedgersResponse.from(accountLedgers);
    }

    @Transactional
    public void delete(Long id) {
        AccountLedgersEntity accountLedgers = this.accountLedgersRepository.findById(id)
                .orElseThrow(() -> new AccountLedgerNotfound(id));

        accountLedgers.setDeletedAt(Instant.now());
    }
}
