package com.possystem.backend.auth.service.impl;

import com.nimbusds.jose.JOSEException;
import com.possystem.backend.auth.dto.*;
import com.possystem.backend.auth.entity.InvalidatedToken;
import com.possystem.backend.auth.repository.InvalidatedTokenRepository;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.role.entity.Role;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.ParseException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvalidatedTokenRepository invalidatedTokenRepository;

    @InjectMocks
    private AuthenticationServiceImpl authenticationService;

    private User testUser;
    private final String rawPassword = "password123";
    private final String signerKey = "test-secret-key-must-be-at-least-32-bytes-long-for-hmac-sha256-pos-system";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authenticationService, "signerKey", signerKey);
        ReflectionTestUtils.setField(authenticationService, "validDuration", 3600L);
        ReflectionTestUtils.setField(authenticationService, "refreshableDuration", 86400L);

        String encodedPassword = new BCryptPasswordEncoder(10).encode(rawPassword);
        Role role = Role.builder().name("EMPLOYEE").build();

        testUser = User.builder()
                .username("cashier01")
                .password(encodedPassword)
                .fullName("Nguyen Van A")
                .roles(Set.of(role))
                .build();
    }

    @Test
    @DisplayName("Unit Test: Đăng nhập thành công với thông tin chính xác")
    void authenticate_Success() {
        when(userRepository.findByUsername("cashier01")).thenReturn(Optional.of(testUser));

        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("cashier01")
                .password(rawPassword)
                .build();

        AuthenticationResponse response = authenticationService.authenticate(request);

        assertNotNull(response);
        assertTrue(response.isAuthenticated());
        assertNotNull(response.getToken());
        assertNotNull(response.getRefreshToken());
        verify(userRepository, times(1)).findByUsername("cashier01");
    }

    @Test
    @DisplayName("Unit Test: Đăng nhập thất bại khi tài khoản không tồn tại")
    void authenticate_UserNotFound_ThrowsException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("unknown")
                .password(rawPassword)
                .build();

        AppException exception = assertThrows(AppException.class, () ->
                authenticationService.authenticate(request)
        );

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(userRepository, times(1)).findByUsername("unknown");
    }

    @Test
    @DisplayName("Unit Test: Đăng nhập thất bại khi sai mật khẩu")
    void authenticate_WrongPassword_ThrowsException() {
        when(userRepository.findByUsername("cashier01")).thenReturn(Optional.of(testUser));

        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("cashier01")
                .password("wrongpassword")
                .build();

        AppException exception = assertThrows(AppException.class, () ->
                authenticationService.authenticate(request)
        );

        assertEquals(ErrorCode.UNAUTHENTICATED, exception.getErrorCode());
    }

    @Test
    @DisplayName("Unit Test: Introspect token hợp lệ trả về valid = true")
    void introspect_ValidToken_ReturnsTrue() throws ParseException, JOSEException {
        when(userRepository.findByUsername("cashier01")).thenReturn(Optional.of(testUser));

        AuthenticationResponse authResponse = authenticationService.authenticate(
                AuthenticationRequest.builder().username("cashier01").password(rawPassword).build()
        );

        IntrospectResponse introspectResponse = authenticationService.introspect(
                IntrospectRequest.builder().token(authResponse.getToken()).build()
        );

        assertNotNull(introspectResponse);
        assertTrue(introspectResponse.isValid());
    }

    @Test
    @DisplayName("Unit Test: Introspect token không hợp lệ trả về valid = false")
    void introspect_InvalidToken_ReturnsFalse() throws ParseException, JOSEException {
        IntrospectResponse introspectResponse = authenticationService.introspect(
                IntrospectRequest.builder().token("invalid.jwt.token").build()
        );

        assertNotNull(introspectResponse);
        assertFalse(introspectResponse.isValid());
    }

    @Test
    @DisplayName("Unit Test: Đăng xuất lưu token vào InvalidatedTokenRepository")
    void logout_Success() throws ParseException, JOSEException {
        when(userRepository.findByUsername("cashier01")).thenReturn(Optional.of(testUser));

        AuthenticationResponse authResponse = authenticationService.authenticate(
                AuthenticationRequest.builder().username("cashier01").password(rawPassword).build()
        );

        LogoutRequest logoutRequest = LogoutRequest.builder()
                .token(authResponse.getToken())
                .refreshToken(authResponse.getRefreshToken())
                .build();

        assertDoesNotThrow(() -> authenticationService.logout(logoutRequest));
        verify(invalidatedTokenRepository, atLeastOnce()).save(any(InvalidatedToken.class));
    }
}

