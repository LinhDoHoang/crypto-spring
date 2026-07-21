package com.crypto.crypto.entities;

import com.crypto.crypto.feature.orders.constant.CloseReasonEnum;
import com.crypto.crypto.feature.orders.constant.OrderSideEnum;
import com.crypto.crypto.feature.orders.constant.OrderStatusEnum;
import com.crypto.crypto.feature.orders.constant.OrderTypeEnum;
import com.crypto.crypto.feature.orders.constant.SizingModeEnum;
import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OrdersEntity extends ModifiedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "client_order_id", length = 100)
    private String clientOrderId;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "symbol", nullable = false, columnDefinition = "symbol_enum")
    private SymbolEnum symbol;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, columnDefinition = "side_enum")
    private OrderSideEnum side;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, columnDefinition = "order_type_enum")
    private OrderTypeEnum orderType = OrderTypeEnum.MARKET;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "sizing_mode", nullable = false, columnDefinition = "sizing_mode_enum")
    private SizingModeEnum sizingMode;

    @Column(name = "quantity", nullable = false, precision = 28, scale = 12)
    private BigDecimal quantity;

    @Column(name = "entry_price", nullable = false, precision = 28, scale = 12)
    private BigDecimal entryPrice;

    @Column(name = "entry_mark_timestamp", nullable = false)
    private Instant entryMarkTimestamp;

    @Column(name = "notional", nullable = false, precision = 28, scale = 12)
    private BigDecimal notional;

    @Column(name = "leverage", nullable = false)
    private Integer leverage;

    @Column(name = "initial_margin", nullable = false, precision = 28, scale = 12)
    private BigDecimal initialMargin;

    @Column(name = "maintenance_margin_rate", nullable = false, precision = 10, scale = 8)
    private BigDecimal maintenanceMarginRate;

    @Column(name = "take_profit", precision = 28, scale = 12)
    private BigDecimal takeProfit;

    @Column(name = "stop_loss", precision = 28, scale = 12)
    private BigDecimal stopLoss;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "orders_status_enum")
    private OrderStatusEnum status = OrderStatusEnum.OPEN;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(EnumType.STRING)
    @Column(name = "close_reason", columnDefinition = "close_reason_enum")
    private CloseReasonEnum closeReason;

    @Column(name = "close_price", precision = 28, scale = 12)
    private BigDecimal closePrice;

    @Column(name = "realized_pnl", precision = 28, scale = 8)
    private BigDecimal realizedPnl;

    @Builder.Default
    @Column(name = "trading_fee", nullable = false, precision = 28, scale = 8)
    private BigDecimal tradingFee = BigDecimal.ZERO;

    @Column(name = "client_mark", precision = 28, scale = 12)
    private BigDecimal clientMark;

    @Column(name = "client_timestamp")
    private Instant clientTimestamp;

    @Column(name = "max_slippage_bps")
    private Integer maxSlippageBps;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Builder.Default
    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;
}
