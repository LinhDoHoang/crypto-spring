package com.crypto.crypto.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class UsersEntity extends ModifiedEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "email", nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Builder.Default
    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @OneToOne(mappedBy = "user")
    private TradingAccountsEntity tradingAccount;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<OrdersEntity> orders;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<RefreshTokensEntity> refreshTokens;
}
