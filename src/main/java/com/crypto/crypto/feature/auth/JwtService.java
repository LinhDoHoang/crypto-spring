package com.crypto.crypto.feature.auth;

import com.crypto.crypto.entities.UsersEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class JwtService {
    private final JwtEncoder jwtEncoder;
    private final String issuer;
    private final String audience;
    private final Duration accessTokenTtl;

    public JwtService(
            JwtEncoder jwtEncoder,
            @Value("${app.auth.jwt.issuer}") String issuer,
            @Value("${app.auth.jwt.audience}") String audience,
            @Value("${app.auth.jwt.access-token-ttl}") Duration accessTokenTtl
    ) {
        this.jwtEncoder = jwtEncoder;
        this.issuer = issuer;
        this.audience = audience;
        this.accessTokenTtl = accessTokenTtl;
    }

    public AccessToken issue(UsersEntity user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("scope", "USER")
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();

        return new AccessToken(value, accessTokenTtl.toSeconds());
    }

    public record AccessToken(String value, long expiresIn) {
    }
}
