package com.crypto.crypto.feature.tradingAccounts;

import com.crypto.crypto.entities.OrdersEntity;
import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.feature.orders.MarketPriceService;
import com.crypto.crypto.feature.orders.OrderRepository;
import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.OrderStatusEnum;
import com.crypto.crypto.feature.orders.exception.OrderException;
import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import com.crypto.crypto.feature.tradingAccounts.dto.AccountSnapshotResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountSnapshotService {
    private final TradingAccountRepository tradingAccountRepository;
    private final OrderRepository orderRepository;
    private final MarketPriceService marketPriceService;

    public AccountSnapshotService(
            TradingAccountRepository tradingAccountRepository,
            OrderRepository orderRepository,
            MarketPriceService marketPriceService
    ) {
        this.tradingAccountRepository = tradingAccountRepository;
        this.orderRepository = orderRepository;
        this.marketPriceService = marketPriceService;
    }

    @Transactional(readOnly = true)
    public AccountSnapshotResponse get(Long userId) {
        TradingAccountsEntity account = accountOf(userId);
        List<OrdersEntity> openOrders = orderRepository.findByAccountIdAndStatus(
                account.getId(),
                OrderStatusEnum.OPEN
        );

        Map<com.crypto.crypto.feature.orders.constant.SymbolEnum, MarketPriceService.MarketPrice> prices =
                new EnumMap<>(com.crypto.crypto.feature.orders.constant.SymbolEnum.class);
        BigDecimal upnl = BigDecimal.ZERO;
        BigDecimal usedMargin = BigDecimal.ZERO;
        BigDecimal maintenance = BigDecimal.ZERO;
        Instant priceAsOf = null;

        for (OrdersEntity order : openOrders) {
            MarketPriceService.MarketPrice marketPrice = prices.computeIfAbsent(
                    order.getSymbol(),
                    marketPriceService::getFreshPrice
            );
            BigDecimal mark = marketPrice.price();
            BigDecimal positionUpnl = unrealizedPnl(order, mark);
            BigDecimal currentNotional = order.getQuantity()
                    .abs()
                    .multiply(mark);

            upnl = upnl.add(positionUpnl);
            usedMargin = usedMargin.add(order.getInitialMargin());
            maintenance = maintenance.add(
                    currentNotional.multiply(order.getMaintenanceMarginRate())
            );
            priceAsOf = priceAsOf == null || marketPrice.timestamp().isBefore(priceAsOf)
                    ? marketPrice.timestamp()
                    : priceAsOf;
        }

        BigDecimal balance = account.getBalance();
        BigDecimal equity = balance.add(upnl);
        BigDecimal freeMargin = equity.subtract(usedMargin);
        BigDecimal marginLevel = usedMargin.signum() > 0
                ? equity.divide(usedMargin, 8, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : null;

        return new AccountSnapshotResponse(
                account.getId(),
                balance,
                equity,
                freeMargin,
                usedMargin,
                upnl,
                maintenance,
                marginLevel,
                account.getDefaultLeverage(),
                priceAsOf
        );
    }

    public TradingAccountsEntity accountOf(Long userId) {
        return tradingAccountRepository
                .findByUserIdAndAccountTypeAndStatus(
                        userId,
                        AccountTypeEnum.DEMO,
                        StatusEnum.ACTIVE
                )
                .orElseThrow(() -> new OrderException(
                        HttpStatus.NOT_FOUND,
                        "Active demo trading account was not found"
                ));
    }

    private BigDecimal unrealizedPnl(OrdersEntity order, BigDecimal mark) {
        BigDecimal difference = order.getSide() == OrderSideEnum.BUY
                ? mark.subtract(order.getEntryPrice())
                : order.getEntryPrice().subtract(mark);
        return difference.multiply(order.getQuantity());
    }
}
