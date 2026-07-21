package com.crypto.crypto.entities;

import com.crypto.crypto.feature.orders.constant.SymbolEnum;
import com.crypto.crypto.feature.trades.constant.TradeSourceEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TradesEntity extends ModifiedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "time", nullable = false)
    private Instant time;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset", nullable = false)
    private SymbolEnum asset;

    @Column(name = "price", nullable = false, precision = 28, scale = 12)
    private BigDecimal price;

    @Column(name = "quantity", nullable = false, precision = 28, scale = 12)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private TradeSourceEnum source;

    @Column(name = "source_trade_id")
    private Long sourceTradeId;
}
