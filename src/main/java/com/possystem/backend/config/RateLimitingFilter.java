package com.possystem.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.ratelimit.DistributedRateLimiterService;
import com.possystem.backend.common.ratelimit.RateLimitProperties;
import com.possystem.backend.common.ratelimit.RateLimitResult;
import com.possystem.backend.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Multi-tier Distributed Rate Limiting filter backed by Redis Lua Token Bucket.
 * Evaluates limits along three dimensions: User ID, Client IP Address, and API
 * Endpoint Tier.
 * Automatically degrades to local in-memory Token Bucket if Redis is
 * unreachable.
 */
@Slf4j
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ObjectProvider<DistributedRateLimiterService> rateLimiterServiceProvider;
    private final ObjectProvider<RateLimitProperties> propertiesProvider;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public RateLimitingFilter(
            ObjectProvider<DistributedRateLimiterService> rateLimiterServiceProvider,
            ObjectProvider<RateLimitProperties> propertiesProvider) {
        this.rateLimiterServiceProvider = rateLimiterServiceProvider;
        this.propertiesProvider = propertiesProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        DistributedRateLimiterService rateLimiter = rateLimiterServiceProvider.getIfAvailable();
        RateLimitProperties props = propertiesProvider.getIfAvailable();

        if (rateLimiter == null || props == null || !props.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = extractNormalizedPath(request);
        if (isWhitelistedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        TierSpec spec = resolveTier(request.getMethod(), path, props);
        String userId = extractAuthenticatedUserId();
        String clientIp = getClientIp(request);

        RateLimitResult result = rateLimiter.checkRequest(
                spec.tierName,
                spec.endpointGroup,
                userId,
                clientIp,
                spec.maxPerMinute);

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
        response.setHeader("X-RateLimit-Policy",
                spec.tierName + ";q=" + spec.maxPerMinute + ";w=60;mode=" + result.getMode());

        if (!result.isAllowed()) {
            log.warn("Rate limit [{}] exceeded for user='{}' ip='{}' endpoint='{}' (mode={})",
                    spec.tierName, userId != null ? userId : "anonymous", clientIp, path, result.getMode());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(Math.max(1L, result.getRetryAfterSeconds())));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            ApiResponse<Void> apiResponse = ApiResponse.<Void>builder()
                    .code(ErrorCode.TOO_MANY_REQUESTS.getCode())
                    .success(false)
                    .message(ErrorCode.TOO_MANY_REQUESTS.getMessage()
                            + " [Hạn mức " + spec.tierName + ": " + spec.maxPerMinute + "/phút]")
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private TierSpec resolveTier(String method, String path, RateLimitProperties props) {
        if ("POST".equalsIgnoreCase(method) && (path.endsWith("/auth/token") || path.endsWith("/auth/refresh"))) {
            return new TierSpec("AUTH", "auth_token", props.getAuthPerMinute());
        }
        if (("POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method))
                && path.contains("/change-password")) {
            return new TierSpec("AUTH_SENSITIVE", "change_password", props.getAuthPerMinute());
        }
        if (path.contains("/discounts/code/") || path.endsWith("/discounts/apply")) {
            return new TierSpec("VOUCHER_DISCOUNT", "voucher", props.getVoucherPerMinute());
        }
        if (path.startsWith("/payments/webhook")
                || ("POST".equalsIgnoreCase(method) && (path.equals("/orders") || path.equals("/payments")))) {
            return new TierSpec("CHECKOUT_WEBHOOK", "checkout", props.getCheckoutPerMinute());
        }
        if (path.startsWith("/reports")) {
            return new TierSpec("REPORT_HEAVY", "reports", props.getReportPerMinute());
        }
        return new TierSpec("GENERAL_API", method.toUpperCase() + "_" + normalizeEndpointGroup(path),
                props.getApiPerMinute());
    }

    private String normalizeEndpointGroup(String path) {
        String[] segments = path.split("/");
        for (String seg : segments) {
            if (!seg.isBlank()) {
                return seg;
            }
        }
        return "root";
    }

    private String extractNormalizedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            return uri.substring(ctx.length());
        }
        return uri != null ? uri : "/";
    }

    private boolean isWhitelistedPath(String path) {
        return path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws/");
    }

    private String extractAuthenticatedUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equalsIgnoreCase(auth.getName())) {
            return auth.getName();
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String xfHeader = request.getHeader("X-Forwarded-For");

        // Anti-IP spoofing: only trust X-Forwarded-For when originating from local
        // reverse proxy
        if (xfHeader != null && !xfHeader.isBlank() && !"unknown".equalsIgnoreCase(xfHeader)) {
            if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr)) {
                return xfHeader.split(",")[0].trim();
            }
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    private record TierSpec(String tierName, String endpointGroup, int maxPerMinute) {
    }
}
