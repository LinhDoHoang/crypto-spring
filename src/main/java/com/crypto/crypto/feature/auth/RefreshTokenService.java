package com.crypto.crypto.feature.auth;

import com.crypto.crypto.entities.RefreshTokensEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private static final int TOKEN_BYTES = 32;

    private final RefreshtokensRepository refreshTokensRepository;
    private final Duration refreshTokenTtl;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshtokensRepository refreshTokensRepository,
            @Value("${app.auth.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        this.refreshTokensRepository = refreshTokensRepository;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    @Transactional
    public IssuedRefreshToken issue(Long userId, TokenMetadata metadata) {
        return createToken(userId, metadata, Instant.now());
    }

    @Transactional
    public IssuedRefreshToken rotate(String rawToken, TokenMetadata metadata) {
        Instant now = Instant.now();
        RefreshTokensEntity existing = refreshTokensRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(this::invalidToken);

        if (existing.getRevokedAt() != null || !existing.getExpiresAt().isAfter(now)) {
            throw invalidToken();
        }

        existing.setLastUsedAt(now);
        existing.setRevokedAt(now);
        return createToken(existing.getUserId(), metadata, now);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokensRepository.findByTokenHash(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    private IssuedRefreshToken createToken(Long userId, TokenMetadata metadata, Instant now) {
        String rawToken = generateToken();
        RefreshTokensEntity entity = RefreshTokensEntity.builder()
                .userId(userId)
                .tokenHash(hash(rawToken))
                .createdAt(now)
                .expiresAt(now.plus(refreshTokenTtl))
                .deviceName(truncate(metadata.deviceName(), 255))
                .createdByIp(metadata.ipAddress())
                .userAgent(metadata.userAgent())
                .build();

        refreshTokensRepository.save(entity);
        return new IssuedRefreshToken(rawToken, userId);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] value = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(value);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private AuthException invalidToken() {
        return new AuthException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
    }

    public record IssuedRefreshToken(String value, Long userId) {
    }

    public record TokenMetadata(String deviceName, String ipAddress, String userAgent) {
    }
}
