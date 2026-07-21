package com.crypto.crypto.config;

import com.crypto.crypto.constant.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalException {
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(422)
                .body(ApiResponse.failure(message));
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("Invalid request"));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Void>> handleConflict(Exception exception) {
        log.warn("Request conflicted with persisted state: {}", exception.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("Request conflicts with the current resource state"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled application exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("An unexpected error occurred"));
    }
}
