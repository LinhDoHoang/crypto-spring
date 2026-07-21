package com.crypto.crypto.entities;

import com.crypto.crypto.feature.accountLedgers.constant.LedgerTypeEnum;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "account_ledgers")
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AccountLedgersEntity extends ModifiedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private LedgerTypeEnum type;

    @Column(name = "amount", nullable = false, precision = 28, scale = 8)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 28, scale = 8)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 28, scale = 8)
    private BigDecimal balanceAfter;

    @Column(name = "description", length = 500)
    private String description;
}
