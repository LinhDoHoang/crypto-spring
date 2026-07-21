package com.crypto.crypto.entities;

import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "trading_accounts")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TradingAccountsEntity extends ModifiedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(value = EnumType.STRING)
    @Column(name = "account_type", nullable = false, columnDefinition = "account_type_enum")
    private AccountTypeEnum accountType = AccountTypeEnum.DEMO;

    @Builder.Default
    @Column(name = "currency", nullable = false)
    private String currency = "USDT";

    @Builder.Default
    @Column(name = "balance", nullable = false, precision = 24, scale = 8)
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(value = EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "status_enum")
    private StatusEnum status = StatusEnum.ACTIVE;

    @Builder.Default
    @Column(name = "default_leverage", nullable = false)
    private Integer defaultLeverage = 1;

    @Builder.Default
    @Column(name = "version", nullable = false)
    @Version
    private Integer version = 0;
}
