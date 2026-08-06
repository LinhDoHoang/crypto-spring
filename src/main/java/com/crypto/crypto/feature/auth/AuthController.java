package com.crypto.crypto.feature.auth;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.auth.dto.AuthResponseDto;
import com.crypto.crypto.feature.auth.dto.SigninRequestDto;
import com.crypto.crypto.feature.auth.dto.SignupRequestDto;
import com.crypto.crypto.feature.users.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@Validated
@RequestMapping("/auth")
public class AuthController {
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;
    private final Duration refreshTokenTtl;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(
            AuthService authService,
            @Value("${app.auth.refresh-token-ttl}") Duration refreshTokenTtl,
            @Value("${app.auth.cookie.secure}") boolean cookieSecure,
            @Value("${app.auth.cookie.same-site}") String cookieSameSite
    ) {
        this.authService = authService;
        this.refreshTokenTtl = refreshTokenTtl;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponseDto>> signup(
            @Valid @RequestBody SignupRequestDto request,
            HttpServletRequest servletRequest
    ) {
        AuthService.AuthResult result = authService.signup(request, metadata(servletRequest));
        return withRefreshCookie(HttpStatus.CREATED, "Signup successfully", result);
    }

    @PostMapping("/signin")
    public ResponseEntity<ApiResponse<AuthResponseDto>> signin(
            @Valid @RequestBody SigninRequestDto request,
            HttpServletRequest servletRequest
    ) {
        AuthService.AuthResult result = authService.signin(request, metadata(servletRequest));
        return withRefreshCookie(HttpStatus.OK, "Signin successfully", result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponseDto>> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletRequest servletRequest
    ) {
        AuthService.AuthResult result = authService.refresh(refreshToken, metadata(servletRequest));
        return withRefreshCookie(HttpStatus.OK, "Token refreshed successfully", result);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken
    ) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.success(
                "Current user found successfully",
                authService.me(userId)
        ));
    }

    private ResponseEntity<ApiResponse<AuthResponseDto>> withRefreshCookie(
            HttpStatus status,
            String message,
            AuthService.AuthResult result
    ) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.rawRefreshToken()).toString())
                .body(ApiResponse.success(message, result.response()));
    }

    private ResponseCookie refreshCookie(String token) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(refreshTokenTtl)
                .build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();
    }

    private RefreshTokenService.TokenMetadata metadata(HttpServletRequest request) {
        return new RefreshTokenService.TokenMetadata(
                request.getHeader("X-Device-Name"),
                request.getRemoteAddr(),
                request.getHeader(HttpHeaders.USER_AGENT)
        );
    }
}
