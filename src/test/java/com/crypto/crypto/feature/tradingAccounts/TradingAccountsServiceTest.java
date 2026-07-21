package com.crypto.crypto.feature.tradingAccounts;

import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.feature.tradingAccounts.dto.CreateTradingAccountsDto;
import com.crypto.crypto.feature.tradingAccounts.dto.TradingAccountsResponse;
import com.crypto.crypto.feature.tradingAccounts.dto.UpdateTradingAccountsDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingAccountsServiceTest {
    @Mock
    private TradingAccountRepository tradingAccountRepository;

    @InjectMocks
    private TradingAccountsService tradingAccountsService;

    @Test
    void createUsesBigDecimalAndEntityDefaults() {
        CreateTradingAccountsDto dto = new CreateTradingAccountsDto();
        dto.setUserId(2L);
        dto.setBalance(new BigDecimal("5000.12345678"));
        when(tradingAccountRepository.save(any())).thenAnswer(invocation -> {
            TradingAccountsEntity account = invocation.getArgument(0);
            account.setId(1L);
            return account;
        });

        TradingAccountsResponse response = tradingAccountsService.create(dto);

        assertEquals(new BigDecimal("5000.12345678"), response.balance());
        assertEquals("DEMO", response.accountType().name());
        assertEquals("USDT", response.currency());
    }

    @Test
    void nullPatchFieldsDoNotOverwriteExistingValuesAndDeleteIsSoft() {
        TradingAccountsEntity account = TradingAccountsEntity.builder()
                .id(1L)
                .userId(2L)
                .balance(new BigDecimal("5000"))
                .build();
        when(tradingAccountRepository.findById(1L)).thenReturn(Optional.of(account));

        TradingAccountsResponse response = tradingAccountsService.update(1L, new UpdateTradingAccountsDto());
        tradingAccountsService.delete(1L);

        assertEquals(new BigDecimal("5000"), response.balance());
        assertNotNull(account.getDeletedAt());
    }
}
