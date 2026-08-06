package com.crypto.crypto.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class RefreshTokensEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "device_name", length = 255)
    private String deviceName;

    @Column(name = "created_by_ip")
    private String createdByIp;

    @Column(name = "user_agent")
    private String userAgent;
    
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            referencedColumnName = "id",
            nullable = false,
            insertable = false,
            updatable = false,
            name = "user_id"
    )
    private UsersEntity user;
}
