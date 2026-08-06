package com.crypto.crypto.feature.auth;

import com.crypto.crypto.entities.AccountLedgersEntity;
import com.crypto.crypto.entities.TradingAccountsEntity;
import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.accountLedgers.AccountLedgersRepository;
import com.crypto.crypto.feature.auth.dto.SigninRequestDto;
import com.crypto.crypto.feature.auth.dto.SignupRequestDto;
import com.crypto.crypto.feature.tradingAccounts.TradingAccountRepository;
import com.crypto.crypto.feature.users.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private UsersRepository usersRepository;
    @Mock
    private TradingAccountRepository tradingAccountRepository;
    @Mock
    private AccountLedgersRepository accountLedgersRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                usersRepository,
                tradingAccountRepository,
                accountLedgersRepository,
                passwordEncoder,
                authenticationManager,
                jwtService,
                refreshTokenService
        );
    }

    @Test
    void signupHashesPasswordAndCreatesDemoAccount() {
        when(usersRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("{bcrypt}hash");
        when(usersRepository.save(any())).thenAnswer(invocation -> {
            UsersEntity user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });
        when(tradingAccountRepository.save(any())).thenAnswer(invocation -> {
            TradingAccountsEntity account = invocation.getArgument(0);
            account.setId(10L);
            return account;
        });
        when(jwtService.issue(any())).thenReturn(new JwtService.AccessToken("access-token", 900));
        when(refreshTokenService.issue(any(), any()))
                .thenReturn(new RefreshTokenService.IssuedRefreshToken("refresh-token", 1L));

        AuthService.AuthResult result = authService.signup(
                new SignupRequestDto("  USER@Example.com ", "secret123"),
                new RefreshTokenService.TokenMetadata("browser", "127.0.0.1", "JUnit")
        );

        ArgumentCaptor<UsersEntity> userCaptor = ArgumentCaptor.forClass(UsersEntity.class);
        verify(usersRepository).save(userCaptor.capture());
        assertEquals("user@example.com", userCaptor.getValue().getEmail());
        assertEquals("{bcrypt}hash", userCaptor.getValue().getPasswordHash());

        ArgumentCaptor<TradingAccountsEntity> accountCaptor =
                ArgumentCaptor.forClass(TradingAccountsEntity.class);
        verify(tradingAccountRepository).save(accountCaptor.capture());
        assertEquals(1L, accountCaptor.getValue().getUserId());
        assertEquals(new BigDecimal("5000.00"), accountCaptor.getValue().getBalance());

        ArgumentCaptor<AccountLedgersEntity> ledgerCaptor =
                ArgumentCaptor.forClass(AccountLedgersEntity.class);
        verify(accountLedgersRepository).save(ledgerCaptor.capture());
        assertEquals(10L, ledgerCaptor.getValue().getAccountId());
        assertEquals(new BigDecimal("5000.00"), ledgerCaptor.getValue().getAmount());
        assertEquals("access-token", result.response().accessToken());
        assertEquals("refresh-token", result.rawRefreshToken());
    }

    @Test
    void signinReturnsGenericUnauthorizedErrorForBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("internal detail"));

        AuthException exception = assertThrows(AuthException.class, () -> authService.signin(
                new SigninRequestDto("user@example.com", "wrong-password"),
                new RefreshTokenService.TokenMetadata(null, "127.0.0.1", "JUnit")
        ));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
        assertEquals("Invalid email or password", exception.getMessage());
    }
}
