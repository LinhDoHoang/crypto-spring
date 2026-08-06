package com.crypto.crypto.config;

import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtConfigTest {
    @Test
    void issuedTokenIsValidatedWithExpectedClaims() {
        JwtConfig config = new JwtConfig();
        String encodedSecret = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes()
        );
        SecretKey secretKey = config.jwtSecretKey(encodedSecret);
        JwtEncoder encoder = config.jwtEncoder(secretKey);
        JwtDecoder decoder = config.jwtDecoder(secretKey, "https://crypto-api.local", "crypto-web");
        JwtService service = new JwtService(
                encoder,
                "https://crypto-api.local",
                "crypto-web",
                Duration.ofMinutes(15)
        );

        JwtService.AccessToken accessToken = service.issue(UsersEntity.builder()
                .id(42L)
                .email("user@example.com")
                .passwordHash("unused")
                .enabled(true)
                .build());
        Jwt jwt = decoder.decode(accessToken.value());

        assertEquals("42", jwt.getSubject());
        assertEquals("https://crypto-api.local", jwt.getIssuer().toString());
        assertTrue(jwt.getAudience().contains("crypto-web"));
        assertEquals("USER", jwt.getClaimAsString("scope"));
        assertEquals(900, accessToken.expiresIn());
    }
}
