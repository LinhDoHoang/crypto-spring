package com.crypto.crypto.feature.orders;

import com.crypto.crypto.entities.OrdersEntity;
import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.accountLedgers.AccountLedgersRepository;
import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.SizingModeEnum;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.orders.dto.PlaceOrderRequest;
import com.crypto.crypto.feature.orders.exception.OrderException;
import com.crypto.crypto.feature.tradingAccounts.TradingAccountRepository;
import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrdersServiceCreateTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradingAccountRepository tradingAccountRepository;

    @Mock
    private AccountLedgersRepository accountLedgersRepository;

    @Mock
    private MarketPriceService marketPriceService;

    private OrdersService ordersService;

    @BeforeEach
    void setUp() {
        ordersService = new OrdersService(
                orderRepository,
                tradingAccountRepository,
                accountLedgersRepository,
                marketPriceService,
                new BigDecimal("0.005"),
                5
        );
    }

    @Test
    void createsOpenOrderUsingServerPriceAndCalculatedMargin() {
        UsersEntity user = UsersEntity.builder()
                .id(7L)
                .enabled(true)
                .build();
        TradingAccountsEntity account = TradingAccountsEntity.builder()
                .id(11L)
                .userId(7L)
                .accountType(AccountTypeEnum.DEMO)
                .status(StatusEnum.ACTIVE)
                .balance(new BigDecimal("5000.00"))
                .build();
        PlaceOrderRequest request = request();

        when(marketPriceService.getFreshPrice(SymbolEnum.BTCUSDT))
                .thenReturn(new MarketPriceService.MarketPrice(
                        SymbolEnum.BTCUSDT,
                        new BigDecimal("65000.00"),
                        Instant.parse("2026-08-09T00:00:00Z")
                ));
        when(tradingAccountRepository.findForUpdate(
                7L,
                AccountTypeEnum.DEMO,
                StatusEnum.ACTIVE
        )).thenReturn(Optional.of(account));
        when(orderRepository.findByAccountIdAndStatus(11L, com.crypto.crypto.feature.orders.constant.OrderStatusEnum.OPEN))
                .thenReturn(List.of());
        when(orderRepository.save(any(OrdersEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ordersService.create(user, request, "order-1");

        ArgumentCaptor<OrdersEntity> captor = ArgumentCaptor.forClass(OrdersEntity.class);
        verify(orderRepository).save(captor.capture());
        OrdersEntity saved = captor.getValue();

        assertThat(saved.getAccountId()).isEqualTo(11L);
        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getEntryPrice()).isEqualByComparingTo("65000.00");
        assertThat(saved.getQuantity()).isEqualByComparingTo("0.010000000000");
        assertThat(saved.getNotional()).isEqualByComparingTo("650.000000000000");
        assertThat(saved.getInitialMargin()).isEqualByComparingTo("65.000000000000");
    }

    @Test
    void rejectsOrderWhenFreeMarginIsInsufficient() {
        UsersEntity user = UsersEntity.builder()
                .id(7L)
                .enabled(true)
                .build();
        TradingAccountsEntity account = TradingAccountsEntity.builder()
                .id(11L)
                .userId(7L)
                .accountType(AccountTypeEnum.DEMO)
                .status(StatusEnum.ACTIVE)
                .balance(new BigDecimal("10.00"))
                .build();

        when(marketPriceService.getFreshPrice(SymbolEnum.BTCUSDT))
                .thenReturn(new MarketPriceService.MarketPrice(
                        SymbolEnum.BTCUSDT,
                        new BigDecimal("65000.00"),
                        Instant.parse("2026-08-09T00:00:00Z")
                ));
        when(tradingAccountRepository.findForUpdate(
                7L,
                AccountTypeEnum.DEMO,
                StatusEnum.ACTIVE
        )).thenReturn(Optional.of(account));
        when(orderRepository.findByAccountIdAndStatus(11L, com.crypto.crypto.feature.orders.constant.OrderStatusEnum.OPEN))
                .thenReturn(List.of());

        assertThatThrownBy(() -> ordersService.create(user, request(), "order-1"))
                .isInstanceOf(OrderException.class)
                .hasMessage("Insufficient free margin");

        verify(orderRepository, never()).save(any(OrdersEntity.class));
    }

    private PlaceOrderRequest request() {
        PlaceOrderRequest request = new PlaceOrderRequest();
        request.setSymbol(SymbolEnum.BTCUSDT);
        request.setSide(OrderSideEnum.BUY);
        request.setMode(SizingModeEnum.UNITS);
        request.setQtyUnits(new BigDecimal("0.01"));
        request.setLeverage(10);
        request.setClientMark(new BigDecimal("65000.00"));
        return request;
    }
}
