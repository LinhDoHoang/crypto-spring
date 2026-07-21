package com.crypto.crypto.feature.orders;

import com.crypto.crypto.entities.OrdersEntity;
import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.SizingModeEnum;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.orders.dto.CreateOrderDto;
import com.crypto.crypto.feature.orders.dto.OrderResponse;
import com.crypto.crypto.feature.orders.dto.UpdateOrderDto;
import com.crypto.crypto.feature.orders.exception.OrderNotFoundException;
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
class OrdersServiceTest {
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrdersService ordersService;

    @Test
    void createUsesBigDecimalAndDefaults() {
        CreateOrderDto dto = createDto();
        when(orderRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            OrdersEntity order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });

        OrderResponse response = ordersService.create(dto);

        assertEquals(1L, response.id());
        assertEquals(new BigDecimal("65000.125"), response.entryPrice());
        assertEquals("MARKET", response.orderType().name());
        assertEquals(BigDecimal.ZERO, response.tradingFee());
    }

    @Test
    void updateChangesOnlyProvidedFields() {
        OrdersEntity order = existingOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        UpdateOrderDto dto = new UpdateOrderDto();
        dto.setLeverage(20);

        OrderResponse response = ordersService.update(1L, dto);

        assertEquals(20, response.leverage());
        assertEquals(SymbolEnum.BTCUSDT, response.symbol());
    }

    @Test
    void deleteSoftDeletesOrder() {
        OrdersEntity order = existingOrder();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ordersService.delete(1L);

        assertNotNull(order.getDeletedAt());
    }

    @Test
    void getOneThrowsForMissingOrder() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class, () -> ordersService.getOne(99L));
    }

    private CreateOrderDto createDto() {
        CreateOrderDto dto = new CreateOrderDto();
        dto.setAccountId(1L);
        dto.setUserId(2L);
        dto.setSymbol(SymbolEnum.BTCUSDT);
        dto.setSide(OrderSideEnum.BUY);
        dto.setSizingMode(SizingModeEnum.UNITS);
        dto.setQuantity(new BigDecimal("0.01"));
        dto.setEntryPrice(new BigDecimal("65000.125"));
        dto.setEntryMarkTimestamp(Instant.parse("2026-07-20T10:00:00Z"));
        dto.setNotional(new BigDecimal("650.00125"));
        dto.setLeverage(10);
        dto.setInitialMargin(new BigDecimal("65.000125"));
        dto.setMaintenanceMarginRate(new BigDecimal("0.005"));
        dto.setOpenedAt(Instant.parse("2026-07-20T10:00:00Z"));
        return dto;
    }

    private OrdersEntity existingOrder() {
        return OrdersEntity.builder()
                .id(1L)
                .accountId(1L)
                .userId(2L)
                .symbol(SymbolEnum.BTCUSDT)
                .side(OrderSideEnum.BUY)
                .sizingMode(SizingModeEnum.UNITS)
                .quantity(new BigDecimal("0.01"))
                .entryPrice(new BigDecimal("65000"))
                .entryMarkTimestamp(Instant.now())
                .notional(new BigDecimal("650"))
                .leverage(10)
                .initialMargin(new BigDecimal("65"))
                .maintenanceMarginRate(new BigDecimal("0.005"))
                .openedAt(Instant.now())
                .build();
    }
}
