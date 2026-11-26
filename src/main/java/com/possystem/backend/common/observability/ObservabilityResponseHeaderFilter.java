package com.possystem.backend.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Attaches the active non-PII `X-Trace-Id` response header before the HTTP response commits
 * so browser clients and API consumers can correlate requests with distributed traces and logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ObservabilityResponseHeaderFilter extends OncePerRequestFilter {

    private final ObjectProvider<Tracer> tracerProvider;

    public ObservabilityResponseHeaderFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveActiveTraceId();
        if (traceId != null && !traceId.isBlank() && !response.containsHeader("X-Trace-Id")) {
            response.setHeader("X-Trace-Id", traceId);
        }
        filterChain.doFilter(request, response);
    }

    private String resolveActiveTraceId() {
        Tracer tracer = tracerProvider != null ? tracerProvider.getIfAvailable() : null;
        if (tracer != null) {
            Span current = tracer.currentSpan();
            if (current != null && current.context() != null) {
                String tid = current.context().traceId();
                if (tid != null && !tid.isBlank()) {
                    return tid;
                }
            }
        }
        String mdcTraceId = MDC.get("traceId");
        return (mdcTraceId != null && !mdcTraceId.isBlank()) ? mdcTraceId : null;
    }
}
