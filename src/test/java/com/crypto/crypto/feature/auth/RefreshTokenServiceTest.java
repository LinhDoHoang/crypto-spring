package com.crypto.crypto.feature.auth;

import com.crypto.crypto.entities.RefreshTokensEntity;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {
    @Test
    void rotateRevokesExistingTokenAndCreatesReplacement() {
        RefreshtokensRepository repository = mock(RefreshtokensRepository.class);
        RefreshTokenService service = new RefreshTokenService(repository, Duration.ofDays(30));
        String rawToken = "old-refresh-token";
        RefreshTokensEntity existing = RefreshTokensEntity.builder()
                .userId(7L)
                .tokenHash(service.hash(rawToken))
                .createdAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(repository.findByTokenHash(service.hash(rawToken))).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshTokenService.IssuedRefreshToken replacement = service.rotate(
                rawToken,
                new RefreshTokenService.TokenMetadata("browser", "127.0.0.1", "JUnit")
        );

        assertEquals(7L, replacement.userId());
        assertNotEquals(rawToken, replacement.value());
        assertNotNull(existing.getRevokedAt());
        assertNotNull(existing.getLastUsedAt());
        verify(repository).save(any(RefreshTokensEntity.class));
    }
}
