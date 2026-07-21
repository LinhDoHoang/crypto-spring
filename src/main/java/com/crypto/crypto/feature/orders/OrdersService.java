package com.crypto.crypto.feature.orders;

import com.crypto.crypto.entities.OrdersEntity;
import com.crypto.crypto.feature.orders.dto.CreateOrderDto;
import com.crypto.crypto.feature.orders.dto.OrderResponse;
import com.crypto.crypto.feature.orders.dto.UpdateOrderDto;
import com.crypto.crypto.feature.orders.exception.OrderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OrdersService {
    private final OrderRepository orderRepository;

    OrdersService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderDto dto) {
        var builder = OrdersEntity.builder()
                .accountId(dto.getAccountId())
                .userId(dto.getUserId())
                .clientOrderId(dto.getClientOrderId())
                .symbol(dto.getSymbol())
                .side(dto.getSide())
                .sizingMode(dto.getSizingMode())
                .quantity(dto.getQuantity())
                .entryPrice(dto.getEntryPrice())
                .entryMarkTimestamp(dto.getEntryMarkTimestamp())
                .notional(dto.getNotional())
                .leverage(dto.getLeverage())
                .initialMargin(dto.getInitialMargin())
                .maintenanceMarginRate(dto.getMaintenanceMarginRate())
                .takeProfit(dto.getTakeProfit())
                .stopLoss(dto.getStopLoss())
                .closeReason(dto.getCloseReason())
                .closePrice(dto.getClosePrice())
                .realizedPnl(dto.getRealizedPnl())
                .clientMark(dto.getClientMark())
                .clientTimestamp(dto.getClientTimestamp())
                .maxSlippageBps(dto.getMaxSlippageBps())
                .openedAt(dto.getOpenedAt())
                .closedAt(dto.getClosedAt());

        if (dto.getOrderType() != null) {
            builder.orderType(dto.getOrderType());
        }
        if (dto.getStatus() != null) {
            builder.status(dto.getStatus());
        }
        if (dto.getTradingFee() != null) {
            builder.tradingFee(dto.getTradingFee());
        }

        OrdersEntity order = orderRepository.save(builder.build());
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOne(Long id) {
        return OrderResponse.from(findById(id));
    }

    @Transactional
    public OrderResponse update(Long id, UpdateOrderDto dto) {
        OrdersEntity order = findById(id);

        if (dto.getAccountId() != null) order.setAccountId(dto.getAccountId());
        if (dto.getUserId() != null) order.setUserId(dto.getUserId());
        if (dto.getClientOrderId() != null) order.setClientOrderId(dto.getClientOrderId());
        if (dto.getSymbol() != null) order.setSymbol(dto.getSymbol());
        if (dto.getSide() != null) order.setSide(dto.getSide());
        if (dto.getOrderType() != null) order.setOrderType(dto.getOrderType());
        if (dto.getSizingMode() != null) order.setSizingMode(dto.getSizingMode());
        if (dto.getQuantity() != null) order.setQuantity(dto.getQuantity());
        if (dto.getEntryPrice() != null) order.setEntryPrice(dto.getEntryPrice());
        if (dto.getEntryMarkTimestamp() != null) order.setEntryMarkTimestamp(dto.getEntryMarkTimestamp());
        if (dto.getNotional() != null) order.setNotional(dto.getNotional());
        if (dto.getLeverage() != null) order.setLeverage(dto.getLeverage());
        if (dto.getInitialMargin() != null) order.setInitialMargin(dto.getInitialMargin());
        if (dto.getMaintenanceMarginRate() != null) order.setMaintenanceMarginRate(dto.getMaintenanceMarginRate());
        if (dto.getTakeProfit() != null) order.setTakeProfit(dto.getTakeProfit());
        if (dto.getStopLoss() != null) order.setStopLoss(dto.getStopLoss());
        if (dto.getStatus() != null) order.setStatus(dto.getStatus());
        if (dto.getCloseReason() != null) order.setCloseReason(dto.getCloseReason());
        if (dto.getClosePrice() != null) order.setClosePrice(dto.getClosePrice());
        if (dto.getRealizedPnl() != null) order.setRealizedPnl(dto.getRealizedPnl());
        if (dto.getTradingFee() != null) order.setTradingFee(dto.getTradingFee());
        if (dto.getClientMark() != null) order.setClientMark(dto.getClientMark());
        if (dto.getClientTimestamp() != null) order.setClientTimestamp(dto.getClientTimestamp());
        if (dto.getMaxSlippageBps() != null) order.setMaxSlippageBps(dto.getMaxSlippageBps());
        if (dto.getOpenedAt() != null) order.setOpenedAt(dto.getOpenedAt());
        if (dto.getClosedAt() != null) order.setClosedAt(dto.getClosedAt());

        return OrderResponse.from(order);
    }

    @Transactional
    public void delete(Long id) {
        OrdersEntity order = findById(id);
        order.setDeletedAt(Instant.now());
    }

    private OrdersEntity findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
