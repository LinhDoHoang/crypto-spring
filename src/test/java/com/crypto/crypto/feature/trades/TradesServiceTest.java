package com.crypto.crypto.feature.trades;

import com.crypto.crypto.entities.TradesEntity;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.trades.constant.TradeSourceEnum;
import com.crypto.crypto.feature.trades.dto.CreateTradeDto;
import com.crypto.crypto.feature.trades.dto.TradeResponse;
import com.crypto.crypto.feature.trades.dto.UpdateTradeDto;
import com.crypto.crypto.feature.trades.exception.TradeNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradesServiceTest {
    @Mock
    private TradesRepository tradesRepository;

    @InjectMocks
    private TradesService tradesService;

    @Test
    void createPreservesDecimalPrecision() {
        CreateTradeDto dto = new CreateTradeDto();
        dto.setTime(Instant.parse("2026-07-20T10:00:00Z"));
        dto.setAsset(SymbolEnum.ETHUSDT);
        dto.setPrice(new BigDecimal("3125.123456789012"));
        dto.setQuantity(new BigDecimal("0.000000000123"));
        dto.setSource(TradeSourceEnum.BINANCE);
        dto.setSourceTradeId(10L);
        when(tradesRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            TradesEntity trade = invocation.getArgument(0);
            trade.setId(1L);
            return trade;
        });

        TradeResponse response = tradesService.create(dto);

        assertEquals(new BigDecimal("3125.123456789012"), response.price());
        assertEquals(new BigDecimal("0.000000000123"), response.quantity());
    }

    @Test
    void updateChangesOnlyProvidedFields() {
        TradesEntity trade = existingTrade();
        when(tradesRepository.findById(1L)).thenReturn(Optional.of(trade));
        UpdateTradeDto dto = new UpdateTradeDto();
        dto.setPrice(new BigDecimal("70000.25"));

        TradeResponse response = tradesService.update(1L, dto);

        assertEquals(new BigDecimal("70000.25"), response.price());
        assertEquals(SymbolEnum.BTCUSDT, response.asset());
    }

    @Test
    void deleteSoftDeletesTrade() {
        TradesEntity trade = existingTrade();
        when(tradesRepository.findById(1L)).thenReturn(Optional.of(trade));

        tradesService.delete(1L);

        assertNotNull(trade.getDeletedAt());
    }

    @Test
    void getOneThrowsForMissingTrade() {
        when(tradesRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(TradeNotFoundException.class, () -> tradesService.getOne(99L));
    }

    private TradesEntity existingTrade() {
        return TradesEntity.builder()
                .id(1L)
                .time(Instant.now())
                .asset(SymbolEnum.BTCUSDT)
                .price(new BigDecimal("65000"))
                .quantity(new BigDecimal("0.01"))
                .source(TradeSourceEnum.BINANCE)
                .sourceTradeId(10L)
                .build();
    }
}
