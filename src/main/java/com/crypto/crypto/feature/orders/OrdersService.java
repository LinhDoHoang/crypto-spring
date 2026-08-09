package com.crypto.crypto.feature.orders;

import com.crypto.crypto.entities.OrdersEntity;
import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.entities.AccountLedgersEntity;
import com.crypto.crypto.feature.accountLedgers.AccountLedgersRepository;
import com.crypto.crypto.feature.accountLedgers.constant.LedgerTypeEnum;
import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.CloseReasonEnum;
import com.crypto.crypto.feature.orders.constant.OrderStatusEnum;
import com.crypto.crypto.feature.orders.constant.OrderTypeEnum;
import com.crypto.crypto.feature.orders.constant.SizingModeEnum;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.orders.dto.PlaceOrderRequest;
import com.crypto.crypto.feature.orders.dto.OrderResponse;
import com.crypto.crypto.feature.orders.dto.PositionResponse;
import com.crypto.crypto.feature.orders.exception.OrderException;
import com.crypto.crypto.feature.orders.exception.OrderNotFoundException;
import com.crypto.crypto.feature.tradingAccounts.TradingAccountRepository;
import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class OrdersService {
    private static final int CALCULATION_SCALE = 12;
    private static final RoundingMode CALCULATION_ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final OrderRepository orderRepository;
    private final TradingAccountRepository tradingAccountRepository;
    private final AccountLedgersRepository accountLedgersRepository;
    private final MarketPriceService marketPriceService;
    private final BigDecimal maintenanceMarginRate;
    private final int defaultMaxSlippageBps;

    OrdersService(
            OrderRepository orderRepository,
            TradingAccountRepository tradingAccountRepository,
            AccountLedgersRepository accountLedgersRepository,
            MarketPriceService marketPriceService,
            @Value("${trading.maintenance-margin-rate:0.005}") BigDecimal maintenanceMarginRate,
            @Value("${trading.default-max-slippage-bps:5}") int defaultMaxSlippageBps
    ) {
        this.orderRepository = orderRepository;
        this.tradingAccountRepository = tradingAccountRepository;
        this.accountLedgersRepository = accountLedgersRepository;
        this.marketPriceService = marketPriceService;
        this.maintenanceMarginRate = maintenanceMarginRate;
        this.defaultMaxSlippageBps = defaultMaxSlippageBps;
    }

    @Transactional
    public OrderResponse create(
            UsersEntity user,
            PlaceOrderRequest request,
            String idempotencyKey
    ) {
        if (user == null || user.getId() == null) {
            throw new OrderException(HttpStatus.UNAUTHORIZED, "User is unavailable");
        }

        if (request == null || request.getSymbol() == null) {
            throw invalid("Symbol is required");
        }

        MarketPriceService.MarketPrice marketPrice =
                marketPriceService.getFreshPrice(request.getSymbol());

        validateRequest(request, marketPrice.price());

        BigDecimal quantity = calculateQuantity(request, marketPrice.price());
        BigDecimal notional = money(
                quantity.abs().multiply(marketPrice.price())
        );
        BigDecimal initialMargin = money(
                notional.divide(
                        BigDecimal.valueOf(request.getLeverage()),
                        CALCULATION_SCALE,
                        CALCULATION_ROUNDING
                )
        );
        String clientOrderId = normalizeIdempotencyKey(
                idempotencyKey != null ? idempotencyKey : request.getClientOrderId()
        );

        TradingAccountsEntity account = tradingAccountRepository.findForUpdate(
                        user.getId(),
                        AccountTypeEnum.DEMO,
                        StatusEnum.ACTIVE
                )
                .orElseThrow(() -> new OrderException(
                        HttpStatus.NOT_FOUND,
                        "Active demo trading account was not found"
                ));

        if (clientOrderId != null) {
            OrdersEntity existing = orderRepository
                    .findByAccountIdAndClientOrderId(account.getId(), clientOrderId)
                    .orElse(null);

            if (existing != null) {
                if (!sameRequest(existing, request, quantity)) {
                    throw new OrderException(
                            HttpStatus.CONFLICT,
                            "Idempotency key was already used for a different order"
                    );
                }
                return OrderResponse.from(existing);
            }
        }

        AccountSnapshot snapshot = calculateSnapshot(account);
        if (snapshot.freeMargin().compareTo(initialMargin) < 0) {
            throw new OrderException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient free margin"
            );
        }

        Instant now = Instant.now();
        OrdersEntity order = OrdersEntity.builder()
                .accountId(account.getId())
                .tradingAccountId(account.getId())
                .userId(user.getId())
                .clientOrderId(clientOrderId)
                .symbol(request.getSymbol())
                .side(request.getSide())
                .orderType(OrderTypeEnum.MARKET)
                .sizingMode(request.getMode())
                .quantity(quantity)
                .entryPrice(marketPrice.price())
                .entryMarkTimestamp(marketPrice.timestamp())
                .notional(notional)
                .leverage(request.getLeverage())
                .initialMargin(initialMargin)
                .maintenanceMarginRate(maintenanceMarginRate)
                .takeProfit(request.getTp())
                .stopLoss(request.getSl())
                .status(OrderStatusEnum.OPEN)
                .tradingFee(ZERO)
                .clientMark(request.getClientMark())
                .clientTimestamp(toClientTimestamp(request.getClientTs()))
                .maxSlippageBps(resolveMaxSlippage(request.getMaxSlippageBps()))
                .openedAt(now)
                .build();

        OrdersEntity savedOrder = orderRepository.save(order);
        return OrderResponse.from(savedOrder);
    }

    private void validateRequest(PlaceOrderRequest request, BigDecimal serverMark) {
        if (request.getMode() == null) {
            throw invalid("Sizing mode is required");
        }

        if (request.getSide() == null) {
            throw invalid("Order side is required");
        }

        if (request.getLeverage() == null
                || request.getLeverage() < 1
                || request.getLeverage() > 100) {
            throw invalid("Leverage must be between 1 and 100");
        }

        if (request.getMode() == SizingModeEnum.UNITS) {
            requirePositive(request.getQtyUnits(), "Quantity is required for UNITS mode");
        } else {
            requirePositive(request.getNotionalUsd(), "Notional is required for NOTIONAL mode");
        }

        if (request.getClientMark() != null && request.getClientMark().signum() <= 0) {
            throw invalid("Client mark must be positive");
        }

        if (request.getClientMark() != null) {
            BigDecimal slippageBps = serverMark
                    .subtract(request.getClientMark())
                    .abs()
                    .divide(request.getClientMark(), CALCULATION_SCALE, CALCULATION_ROUNDING)
                    .multiply(BigDecimal.valueOf(10_000));

            if (slippageBps.compareTo(
                    BigDecimal.valueOf(resolveMaxSlippage(request.getMaxSlippageBps()))) > 0
            ) {
                throw new OrderException(
                        HttpStatus.CONFLICT,
                        "Market price moved beyond the accepted slippage"
                );
            }
        }

        if (request.getTp() != null) {
            boolean validTp = request.getSide() == OrderSideEnum.BUY
                    ? request.getTp().compareTo(serverMark) > 0
                    : request.getTp().compareTo(serverMark) < 0;
            if (!validTp) {
                throw invalid("Take profit is on the wrong side of the market");
            }
        }

        if (request.getSl() != null) {
            boolean validSl = request.getSide() == OrderSideEnum.BUY
                    ? request.getSl().compareTo(serverMark) < 0
                    : request.getSl().compareTo(serverMark) > 0;
            if (!validSl) {
                throw invalid("Stop loss is on the wrong side of the market");
            }
        }
    }

    private BigDecimal calculateQuantity(
            PlaceOrderRequest request,
            BigDecimal serverMark
    ) {
        BigDecimal quantity = request.getMode() == SizingModeEnum.UNITS
                ? request.getQtyUnits()
                : request.getNotionalUsd().divide(
                        serverMark,
                        CALCULATION_SCALE,
                        CALCULATION_ROUNDING
                );

        if (quantity == null || quantity.signum() <= 0) {
            throw invalid("Calculated quantity must be positive");
        }

        return quantity.setScale(CALCULATION_SCALE, CALCULATION_ROUNDING);
    }

    private AccountSnapshot calculateSnapshot(TradingAccountsEntity account) {
        List<OrdersEntity> openOrders = orderRepository.findByAccountIdAndStatus(
                account.getId(),
                OrderStatusEnum.OPEN
        );
        Map<SymbolEnum, BigDecimal> marks = new EnumMap<>(SymbolEnum.class);
        BigDecimal totalUnrealizedPnl = ZERO;
        BigDecimal usedMargin = ZERO;

        for (OrdersEntity order : openOrders) {
            BigDecimal mark = marks.computeIfAbsent(
                    order.getSymbol(),
                    symbol -> marketPriceService.getFreshPrice(symbol).price()
            );
            totalUnrealizedPnl = totalUnrealizedPnl.add(unrealizedPnl(order, mark));
            usedMargin = usedMargin.add(order.getInitialMargin());
        }

        BigDecimal equity = account.getBalance().add(totalUnrealizedPnl);
        return new AccountSnapshot(
                equity,
                usedMargin,
                equity.subtract(usedMargin)
        );
    }

    private BigDecimal unrealizedPnl(OrdersEntity order, BigDecimal markPrice) {
        BigDecimal difference = order.getSide() == OrderSideEnum.BUY
                ? markPrice.subtract(order.getEntryPrice())
                : order.getEntryPrice().subtract(markPrice);
        return difference.multiply(order.getQuantity());
    }

    private boolean sameRequest(
            OrdersEntity existing,
            PlaceOrderRequest request,
            BigDecimal quantity
    ) {
        return existing.getSymbol() == request.getSymbol()
                && existing.getSide() == request.getSide()
                && existing.getSizingMode() == request.getMode()
                && existing.getLeverage().equals(request.getLeverage())
                && sameDecimal(existing.getQuantity(), quantity)
                && sameDecimal(existing.getTakeProfit(), request.getTp())
                && sameDecimal(existing.getStopLoss(), request.getSl());
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private void requirePositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw invalid(message);
        }
    }

    private OrderException invalid(String message) {
        return new OrderException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 100) {
            throw invalid("Idempotency key must not exceed 100 characters");
        }
        return normalized;
    }

    private int resolveMaxSlippage(Integer requested) {
        return requested == null ? defaultMaxSlippageBps : requested;
    }

    private Instant toClientTimestamp(Long clientTs) {
        return clientTs == null ? null : Instant.ofEpochMilli(clientTs);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(CALCULATION_SCALE, CALCULATION_ROUNDING);
    }

    private record AccountSnapshot(
            BigDecimal equity,
            BigDecimal usedMargin,
            BigDecimal freeMargin
    ) {
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAll(
            UsersEntity user,
            String status,
            String symbol,
            int limit
    ) {
        TradingAccountsEntity account = activeAccount(user.getId());
        OrderStatusEnum requestedStatus = parseStatus(status);
        SymbolEnum requestedSymbol = parseSymbol(symbol);

        return orderRepository.findByAccountIdOrderByOpenedAtDesc(account.getId()).stream()
                .filter(order -> requestedStatus == null || order.getStatus() == requestedStatus)
                .filter(order -> requestedSymbol == null || order.getSymbol() == requestedSymbol)
                .limit(normalizeLimit(limit))
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOne(UsersEntity user, Long id) {
        TradingAccountsEntity account = activeAccount(user.getId());
        OrdersEntity order = orderRepository.findByIdAndAccountIdAndUserId(
                        id,
                        account.getId(),
                        user.getId()
                )
                .orElseThrow(() -> new OrderNotFoundException(id));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<PositionResponse> getPositions(
            UsersEntity user,
            String status,
            String symbol,
            int limit
    ) {
        TradingAccountsEntity account = activeAccount(user.getId());
        OrderStatusEnum requestedStatus = parseStatus(status);
        SymbolEnum requestedSymbol = parseSymbol(symbol);

        return orderRepository.findByAccountIdOrderByOpenedAtDesc(account.getId()).stream()
                .filter(order -> requestedStatus == null || order.getStatus() == requestedStatus)
                .filter(order -> requestedSymbol == null || order.getSymbol() == requestedSymbol)
                .limit(normalizeLimit(limit))
                .map(this::toPositionResponse)
                .toList();
    }

    @Transactional
    public OrderResponse close(UsersEntity user, Long orderId) {
        TradingAccountsEntity account = tradingAccountRepository.findForUpdate(
                        user.getId(),
                        AccountTypeEnum.DEMO,
                        StatusEnum.ACTIVE
                )
                .orElseThrow(() -> new OrderException(
                        HttpStatus.NOT_FOUND,
                        "Active demo trading account was not found"
                ));
        OrdersEntity order = orderRepository.findForUpdate(
                        orderId,
                        account.getId(),
                        user.getId()
                )
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatusEnum.OPEN) {
            throw new OrderException(HttpStatus.CONFLICT, "Order is not open");
        }

        MarketPriceService.MarketPrice marketPrice =
                marketPriceService.getFreshPrice(order.getSymbol());
        BigDecimal rawPnl = realizedPnl(order, marketPrice.price());
        BigDecimal appliedPnl = rawPnl.max(order.getInitialMargin().negate());
        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(appliedPnl).max(BigDecimal.ZERO);

        order.setStatus(OrderStatusEnum.CLOSED);
        order.setCloseReason(CloseReasonEnum.MANUAL);
        order.setClosePrice(marketPrice.price());
        order.setRealizedPnl(appliedPnl);
        order.setTradingFee(BigDecimal.ZERO);
        order.setClosedAt(Instant.now());

        account.setBalance(balanceAfter);
        accountLedgersRepository.save(AccountLedgersEntity.builder()
                .accountId(account.getId())
                .orderId(order.getId())
                .type(LedgerTypeEnum.REALIZED_PNL)
                .amount(appliedPnl)
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .description("Manual order close")
                .build());

        return OrderResponse.from(order);
    }

    private TradingAccountsEntity activeAccount(Long userId) {
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

    private PositionResponse toPositionResponse(OrdersEntity order) {
        BigDecimal mark = order.getStatus() == OrderStatusEnum.OPEN
                ? marketPriceService.getFreshPrice(order.getSymbol()).price()
                : order.getClosePrice();
        BigDecimal upnl = order.getStatus() == OrderStatusEnum.OPEN
                ? unrealizedPnl(order, mark)
                : null;
        return PositionResponse.from(order, mark, upnl);
    }

    private BigDecimal realizedPnl(OrdersEntity order, BigDecimal closePrice) {
        BigDecimal difference = order.getSide() == OrderSideEnum.BUY
                ? closePrice.subtract(order.getEntryPrice())
                : order.getEntryPrice().subtract(closePrice);
        return difference.multiply(order.getQuantity());
    }

    private OrderStatusEnum parseStatus(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("ALL")) {
            return null;
        }
        try {
            return OrderStatusEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw invalid("Unsupported order status");
        }
    }

    private SymbolEnum parseSymbol(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SymbolEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw invalid("Unsupported symbol");
        }
    }

    private int normalizeLimit(int limit) {
        if (limit < 1 || limit > 200) {
            throw invalid("Limit must be between 1 and 200");
        }
        return limit;
    }

    private OrdersEntity findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
