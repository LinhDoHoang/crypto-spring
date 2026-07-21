package com.crypto.crypto.feature.tradingAccounts;

import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.feature.tradingAccounts.dto.CreateTradingAccountsDto;
import com.crypto.crypto.feature.tradingAccounts.dto.TradingAccountsResponse;
import com.crypto.crypto.feature.tradingAccounts.dto.UpdateTradingAccountsDto;
import com.crypto.crypto.feature.tradingAccounts.exception.TradingAccountNotFound;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TradingAccountsService {
    private final TradingAccountRepository tradingAccountRepository;

    TradingAccountsService(TradingAccountRepository tradingAccountRepository) {
        this.tradingAccountRepository = tradingAccountRepository;
    }

    @Transactional
    public TradingAccountsResponse create(CreateTradingAccountsDto createTradingAccountsDto) {
        var builder = TradingAccountsEntity.builder()
                .userId(createTradingAccountsDto.getUserId());

        if (createTradingAccountsDto.getAccountType() != null) {
            builder.accountType(createTradingAccountsDto.getAccountType());
        }

        if (createTradingAccountsDto.getCurrency() != null) {
            builder.currency(createTradingAccountsDto.getCurrency());
        }

        if (createTradingAccountsDto.getBalance() != null) {
            builder.balance(createTradingAccountsDto.getBalance());
        }

        if (createTradingAccountsDto.getStatus() != null) {
            builder.status((createTradingAccountsDto.getStatus()));
        }

        if (createTradingAccountsDto.getDefaultLeverage() != null) {
            builder.defaultLeverage(createTradingAccountsDto.getDefaultLeverage());
        }

        TradingAccountsEntity tradingAccount = builder.build();
        this.tradingAccountRepository.save(tradingAccount);
        return TradingAccountsResponse.from(tradingAccount);
    }

    @Transactional(readOnly = true)
    public List<TradingAccountsResponse> getAll() {
        List<TradingAccountsEntity> tradingAccounts = this.tradingAccountRepository.findAll();
        return tradingAccounts.stream()
                .map(TradingAccountsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TradingAccountsResponse getOne(Long id) {
        TradingAccountsEntity existingTradingAccount = this.tradingAccountRepository.findById(id)
                .orElseThrow(() -> new TradingAccountNotFound(id));

        return TradingAccountsResponse.from(existingTradingAccount);
    }

    @Transactional
    public TradingAccountsResponse update(Long id, UpdateTradingAccountsDto updateTradingAccountsDto) {
        TradingAccountsEntity existingTradingAccount = this.tradingAccountRepository.findById(id)
                .orElseThrow(() -> new TradingAccountNotFound(id));

        if (updateTradingAccountsDto.getUserId() != null) {
            existingTradingAccount.setUserId(updateTradingAccountsDto.getUserId());
        }

        if (updateTradingAccountsDto.getAccountType() != null) {
            existingTradingAccount.setAccountType(updateTradingAccountsDto.getAccountType());
        }

        if (updateTradingAccountsDto.getCurrency() != null) {
            existingTradingAccount.setCurrency(updateTradingAccountsDto.getCurrency());
        }

        if (updateTradingAccountsDto.getBalance() != null) {
            existingTradingAccount.setBalance(updateTradingAccountsDto.getBalance());
        }

        if (updateTradingAccountsDto.getStatus() != null) {
            existingTradingAccount.setStatus(updateTradingAccountsDto.getStatus());
        }

        if (updateTradingAccountsDto.getDefaultLeverage() != null) {
            existingTradingAccount.setDefaultLeverage(updateTradingAccountsDto.getDefaultLeverage());
        }

        return TradingAccountsResponse.from(existingTradingAccount);
    }

    @Transactional
    public void delete(Long id) {
        TradingAccountsEntity existingTradingAccount = this.tradingAccountRepository.findById(id)
                .orElseThrow(() -> new TradingAccountNotFound(id));

        existingTradingAccount.setDeletedAt(Instant.now());
    }
}
