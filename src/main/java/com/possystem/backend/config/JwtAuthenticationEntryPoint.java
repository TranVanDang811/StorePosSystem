package com.possystem.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.response.ApiResponse;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        // Set response properties
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // Clear any existing content
        response.getWriter().flush();

        ApiResponse<?> apiResponse = ApiResponse.error(
                errorCode.getCode(),
                "Token is invalid or expired"
        );

        objectMapper.writeValue(response.getWriter(), apiResponse);
    }
}