package com.possystem.backend.common.exception;

import com.possystem.backend.common.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Unit Test Exception: Xử lý AppException và trả về mã lỗi chuẩn")
    void handleAppException() {
        AppException exception = new AppException(ErrorCode.UNAUTHENTICATED);

        ResponseEntity<ApiResponse<?>> response = exceptionHandler.handleAppException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(ErrorCode.UNAUTHENTICATED.getCode(), response.getBody().getCode());
        assertEquals(ErrorCode.UNAUTHENTICATED.getMessage(), response.getBody().getMessage());
    }

    @Test
    @DisplayName("Unit Test Exception: Xử lý BindException (Lỗi validate dữ liệu DTO)")
    void handleBindException() {
        BindException bindException = new BindException(new Object(), "userCreationRequest");
        bindException.addError(new FieldError("userCreationRequest", "username", "Tên đăng nhập không được để trống"));

        ResponseEntity<ApiResponse<?>> response = exceptionHandler.handleBindException(bindException);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("username"));
        assertTrue(response.getBody().getMessage().contains("Tên đăng nhập không được để trống"));
    }

    @Test
    @DisplayName("Unit Test Exception: Xử lý HttpMessageNotReadableException (Payload JSON sai định dạng)")
    void handleHttpMessageNotReadable() {
        HttpMessageNotReadableException exception = new HttpMessageNotReadableException("Malformed JSON");

        ResponseEntity<ApiResponse<?>> response = exceptionHandler.handleHttpMessageNotReadable(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("Invalid JSON format"));
    }

    @Test
    @DisplayName("Unit Test Exception: Xử lý AccessDeniedException (Không đủ quyền truy cập)")
    void handleAccessDeniedException() {
        AccessDeniedException exception = new AccessDeniedException("Forbidden");

        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handlingAccessDeniedException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), response.getBody().getCode());
    }

    @Test
    @DisplayName("Unit Test Exception: Xử lý Exception chung không làm rò rỉ stack trace")
    void handleGenericException() {
        Exception exception = new RuntimeException("Database connection timeout or internal pointer crash");

        ResponseEntity<ApiResponse<Object>> response = exceptionHandler.handleUncategorized(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(ErrorCode.UNCATEGORIZED_EXCEPTION.getCode(), response.getBody().getCode());
        // Không chứa chi tiết nhạy cảm của runtime exception trong message trả về
        assertFalse(response.getBody().getMessage().contains("pointer crash"));
    }
}
