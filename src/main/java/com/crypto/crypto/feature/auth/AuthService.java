package com.crypto.crypto.feature.auth;

import com.crypto.crypto.entities.AccountLedgersEntity;
import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.accountLedgers.AccountLedgersRepository;
import com.crypto.crypto.feature.accountLedgers.constant.LedgerTypeEnum;
import com.crypto.crypto.feature.auth.dto.AuthResponseDto;
import com.crypto.crypto.feature.auth.dto.SigninRequestDto;
import com.crypto.crypto.feature.auth.dto.SignupRequestDto;
import com.crypto.crypto.feature.tradingAccounts.TradingAccountRepository;
import com.crypto.crypto.feature.users.UsersRepository;
import com.crypto.crypto.feature.users.dto.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class AuthService {
    private static final BigDecimal INITIAL_DEMO_BALANCE = new BigDecimal("5000.00");
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UsersRepository usersRepository;
    private final TradingAccountRepository tradingAccountRepository;
    private final AccountLedgersRepository accountLedgersRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UsersRepository usersRepository,
            TradingAccountRepository tradingAccountRepository,
            AccountLedgersRepository accountLedgersRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.usersRepository = usersRepository;
        this.tradingAccountRepository = tradingAccountRepository;
        this.accountLedgersRepository = accountLedgersRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResult signup(
            SignupRequestDto request,
            RefreshTokenService.TokenMetadata metadata
    ) {
        String email = normalizeEmail(request.email());
        validatePasswordLength(request.password());

        if (usersRepository.existsByEmailIgnoreCase(email)) {
            throw new AuthException(HttpStatus.CONFLICT, "Email is already registered");
        }

        UsersEntity user = usersRepository.save(UsersEntity.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .enabled(true)
                .build());

        TradingAccountsEntity account = tradingAccountRepository.save(
                TradingAccountsEntity.builder()
                        .userId(user.getId())
                        .balance(INITIAL_DEMO_BALANCE)
                        .build()
        );

        accountLedgersRepository.save(AccountLedgersEntity.builder()
                .accountId(account.getId())
                .type(LedgerTypeEnum.INITIAL_DEPOSIT)
                .amount(INITIAL_DEMO_BALANCE)
                .balanceBefore(BigDecimal.ZERO)
                .balanceAfter(INITIAL_DEMO_BALANCE)
                .description("Initial demo account deposit")
                .build());

        return createSession(user, metadata);
    }

    @Transactional
    public AuthResult signin(
            SigninRequestDto request,
            RefreshTokenService.TokenMetadata metadata
    ) {
        String email = normalizeEmail(request.email());
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password())
            );
        } catch (DisabledException exception) {
            throw new AuthException(HttpStatus.FORBIDDEN, "Account is disabled");
        } catch (AuthenticationException exception) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        UsersEntity user = findEnabledUserByEmail(email);
        return createSession(user, metadata);
    }

    @Transactional
    public AuthResult refresh(
            String rawRefreshToken,
            RefreshTokenService.TokenMetadata metadata
    ) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new AuthException(HttpStatus.UNAUTHORIZED, "Refresh token is required");
        }

        RefreshTokenService.IssuedRefreshToken refreshed =
                refreshTokenService.rotate(rawRefreshToken, metadata);

        UsersEntity user = usersRepository.findById(refreshed.userId())
                .filter(entity -> Boolean.TRUE.equals(entity.getEnabled()))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "User is unavailable"));

        JwtService.AccessToken accessToken = jwtService.issue(user);
        return new AuthResult(
                new AuthResponseDto(
                        accessToken.value(),
                        "Bearer",
                        accessToken.expiresIn(),
                        UserResponse.from(user)
                ),
                refreshed.value()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        UsersEntity user = usersRepository.findById(userId)
                .filter(entity -> Boolean.TRUE.equals(entity.getEnabled()))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "User is unavailable"));
        return UserResponse.from(user);
    }

    private AuthResult createSession(
            UsersEntity user,
            RefreshTokenService.TokenMetadata metadata
    ) {
        JwtService.AccessToken accessToken = jwtService.issue(user);
        RefreshTokenService.IssuedRefreshToken refreshToken =
                refreshTokenService.issue(user.getId(), metadata);

        return new AuthResult(
                new AuthResponseDto(
                        accessToken.value(),
                        "Bearer",
                        accessToken.expiresIn(),
                        UserResponse.from(user)
                ),
                refreshToken.value()
        );
    }

    private UsersEntity findEnabledUserByEmail(String email) {
        return usersRepository.findByEmailIgnoreCase(email)
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .orElseThrow(() -> new AuthException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
    }

    private void validatePasswordLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public record AuthResult(AuthResponseDto response, String rawRefreshToken) {
    }
}
