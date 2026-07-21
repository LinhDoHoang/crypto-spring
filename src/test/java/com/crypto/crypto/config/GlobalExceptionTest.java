package com.crypto.crypto.config;

import com.crypto.crypto.constant.ApiResponse;
import com.crypto.crypto.feature.users.exception.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionTest {
    private final GlobalException globalException = new GlobalException();

    @Test
    void userNotFoundReturns404() {
        ResponseEntity<ApiResponse<Void>> response =
                globalException.handleResourceNotFound(new UserNotFoundException(10L));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("User with id 10 was not found", response.getBody().message());
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                globalException.handleUnexpectedException(new RuntimeException("database password leaked"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().success());
        assertEquals("An unexpected error occurred", response.getBody().message());
    }
}
