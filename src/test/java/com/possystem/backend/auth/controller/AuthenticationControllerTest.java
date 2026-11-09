package com.possystem.backend.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.auth.dto.AuthenticationRequest;
import com.possystem.backend.auth.dto.AuthenticationResponse;
import com.possystem.backend.auth.dto.IntrospectRequest;
import com.possystem.backend.auth.dto.IntrospectResponse;
import com.possystem.backend.auth.service.AuthenticationService;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthenticationController.class)
@AutoConfigureMockMvc(addFilters = false) // Bỏ qua security filter để test controller & validation độc lập
@Import(GlobalExceptionHandler.class)
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthenticationService authenticationService;

    @Test
    @DisplayName("REST API Test: Đăng nhập thành công trả về HTTP 200 và JWT token")
    void login_Success() throws Exception {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("cashier01")
                .password("password123")
                .build();

        AuthenticationResponse response = AuthenticationResponse.builder()
                .token("mocked.access.jwt")
                .refreshToken("mocked.refresh.jwt")
                .authenticated(true)
                .build();

        when(authenticationService.authenticate(any(AuthenticationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.token").value("mocked.access.jwt"))
                .andExpect(jsonPath("$.result.refreshToken").value("mocked.refresh.jwt"))
                .andExpect(jsonPath("$.result.authenticated").value(true));
    }

    @Test
    @DisplayName("REST API Test: Validation dữ liệu đầu vào - Trả về HTTP 400 khi để trống username/password")
    void login_ValidationFailure_Returns400() throws Exception {
        AuthenticationRequest invalidRequest = AuthenticationRequest.builder()
                .username("")
                .password("")
                .build();

        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REST API Test: Xử lý ngoại lệ - Trả về HTTP 401 khi sai mật khẩu")
    void login_WrongCredentials_Returns401() throws Exception {
        AuthenticationRequest request = AuthenticationRequest.builder()
                .username("cashier01")
                .password("wrongpassword")
                .build();

        when(authenticationService.authenticate(any(AuthenticationRequest.class)))
                .thenThrow(new AppException(ErrorCode.UNAUTHENTICATED));

        mockMvc.perform(post("/auth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHENTICATED.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHENTICATED.getMessage()));
    }

    @Test
    @DisplayName("REST API Test: Introspect token trả về valid = true")
    void introspect_Success() throws Exception {
        IntrospectRequest request = IntrospectRequest.builder()
                .token("valid.token")
                .build();

        when(authenticationService.introspect(any(IntrospectRequest.class)))
                .thenReturn(IntrospectResponse.builder().valid(true).build());

        mockMvc.perform(post("/auth/introspect")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.valid").value(true));
    }
}

