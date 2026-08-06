package com.crypto.crypto.feature.auth;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.auth.dto.AuthResponseDto;
import com.crypto.crypto.feature.auth.dto.SignupRequestDto;
import com.crypto.crypto.feature.users.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    @Test
    void signupReturnsAccessTokenAndSecureRefreshCookieAttributes() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(
                authService,
                Duration.ofDays(30),
                true,
                "Lax"
        );
        AuthResponseDto body = new AuthResponseDto(
                "access-token",
                "Bearer",
                900,
                new UserResponse(1L, "user@example.com", true)
        );
        when(authService.signup(any(), any()))
                .thenReturn(new AuthService.AuthResult(body, "refresh-token"));

        ResponseEntity<ApiResponse<AuthResponseDto>> response = controller.signup(
                new SignupRequestDto("user@example.com", "secret123"),
                new MockHttpServletRequest()
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("access-token", response.getBody().data().accessToken());

        String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(cookie);
        assertTrue(cookie.contains("refresh_token=refresh-token"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/api/v1/auth"));
    }
}
