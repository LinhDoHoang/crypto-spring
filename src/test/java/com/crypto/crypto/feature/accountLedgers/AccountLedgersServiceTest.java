package com.crypto.crypto.feature.accountLedgers;

import com.crypto.crypto.entities.AccountLedgersEntity;
import com.crypto.crypto.feature.accountLedgers.constant.LedgerTypeEnum;
import com.crypto.crypto.feature.accountLedgers.dto.AccountLedgersResponse;
import com.crypto.crypto.feature.accountLedgers.dto.CreateAccountLedgerDto;
import com.crypto.crypto.feature.accountLedgers.dto.UpdateAccountLedgerDto;
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
class AccountLedgersServiceTest {
    @Mock
    private AccountLedgersRepository accountLedgersRepository;

    @InjectMocks
    private AccountLedgersService accountLedgersService;

    @Test
    void createPreservesFinancialPrecision() {
        CreateAccountLedgerDto dto = new CreateAccountLedgerDto();
        dto.setAccountId(1L);
        dto.setType(LedgerTypeEnum.INITIAL_DEPOSIT);
        dto.setAmount(new BigDecimal("5000.12345678"));
        dto.setBalanceBefore(BigDecimal.ZERO);
        dto.setBalanceAfter(new BigDecimal("5000.12345678"));
        when(accountLedgersRepository.save(any())).thenAnswer(invocation -> {
            AccountLedgersEntity ledger = invocation.getArgument(0);
            ledger.setId(1L);
            return ledger;
        });

        AccountLedgersResponse response = accountLedgersService.create(dto);

        assertEquals(new BigDecimal("5000.12345678"), response.amount());
        assertEquals(LedgerTypeEnum.INITIAL_DEPOSIT, response.type());
    }

    @Test
    void nullPatchFieldsDoNotOverwriteExistingValuesAndDeleteIsSoft() {
        AccountLedgersEntity ledger = AccountLedgersEntity.builder()
                .id(1L)
                .accountId(2L)
                .type(LedgerTypeEnum.MANUAL_ADJUSTMENT)
                .amount(BigDecimal.ONE)
                .balanceBefore(BigDecimal.TEN)
                .balanceAfter(new BigDecimal("11"))
                .build();
        when(accountLedgersRepository.findById(1L)).thenReturn(Optional.of(ledger));

        AccountLedgersResponse response = accountLedgersService.update(1L, new UpdateAccountLedgerDto());
        accountLedgersService.delete(1L);

        assertEquals(BigDecimal.ONE, response.amount());
        assertNotNull(ledger.getDeletedAt());
    }
}
