package com.crypto.crypto.entities;

import com.crypto.crypto.feature.tradingAccounts.constant.AccountTypeEnum;
import com.crypto.crypto.feature.tradingAccounts.constant.StatusEnum;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(value = EnumType.STRING)
    @Column(name = "account_type")
    private AccountTypeEnum accountType;

    @Column(name = "currency")
    private String currency;

    @Column(name = "balance")
    private Float balance;

    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Enumerated(value = EnumType.STRING)
    @Column(name = "status")
    private StatusEnum status;

    @Column(name = "default_leverage")
    private Integer defaultLeverage;

    @Column(name = "version")
    @Version
    private Integer version;
}
