package com.possystem.backend.common.observability;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sanitizes tracing tags, Prometheus metric labels, and failure reasons so
 * PII (phone numbers, customer names, emails) and secrets (JWT, API keys, passwords, cash details)
 * never leak into metrics, distributed trace attributes, or response headers.
 */
public final class TraceSanitizer {

    private static final Pattern W3C_TRACEPARENT_PATTERN =
            Pattern.compile("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");

    private static final Pattern SENSITIVE_KEY_PATTERN =
            Pattern.compile("(?i).*(authorization|jwt|token|password|passwd|secret|api[-_]?key|signature|phone|mobile|email|cash[-_]?received).*");

    private static final Pattern ALPHANUM_LABEL_PATTERN = Pattern.compile("[^A-Z0-9_-]");

    private TraceSanitizer() {
    }

    /**
     * Validates and normalizes an incoming W3C traceparent header (`00-<32hex>-<16hex>-<2hex>`).
     * Rejects non-conforming values (e.g. injected headers or accidental tokens).
     */
    public static String sanitizeTraceParent(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() != 55 || !W3C_TRACEPARENT_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        // Per W3C Trace Context spec, all-zero trace-id or parent-id is invalid
        if (normalized.regionMatches(3, "00000000000000000000000000000000", 0, 32)
                || normalized.regionMatches(36, "0000000000000000", 0, 16)) {
            return null;
        }
        return normalized;
    }

    /**
     * Redacts sensitive attribute/header keys to prevent accidental PII/secret leakage in spans.
     */
    public static String redactAttribute(String key, String value) {
        if (value == null) {
            return null;
        }
        if (key != null && SENSITIVE_KEY_PATTERN.matcher(key).matches()) {
            return "[REDACTED]";
        }
        return value;
    }

    /**
     * Maps raw business/exception reasons into fixed low-cardinality metric labels.
     */
    public static String normalizeReasonCode(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return "UNKNOWN";
        }
        String upper = rawReason.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("STOCK") || upper.contains("TỒN KHO")) {
            return "INSUFFICIENT_STOCK";
        }
        if (upper.contains("CASH") || upper.contains("KHÔNG ĐỦ TIỀN") || upper.contains("INVALID_PAYMENT_AMOUNT")) {
            return "INSUFFICIENT_CASH";
        }
        if (upper.contains("SHIFT") || upper.contains("CA LÀM")) {
            return "NO_OPENED_SHIFT";
        }
        if (upper.contains("ORDER_NOT_FOUND") || upper.contains("ĐƠN HÀNG KHÔNG TỒN TẠI")) {
            return "ORDER_NOT_FOUND";
        }
        if (upper.contains("POINT") || upper.contains("ĐIỂM")) {
            return "INSUFFICIENT_POINTS";
        }
        if (upper.contains("DISCOUNT") || upper.contains("VOUCHER") || upper.contains("COUPON")) {
            return "INVALID_VOUCHER";
        }
        if (upper.contains("DUPLICATE") || upper.contains("ALREADY_EXISTS")) {
            return "DUPLICATE_REQUEST";
        }
        if (upper.contains("EXPIRED")) {
            return "AUTO_EXPIRED";
        }
        if (upper.contains("CANCEL")) {
            return "CASHIER_CANCELLED";
        }
        return "BUSINESS_OR_SYSTEM_ERROR";
    }

    /**
     * Bounds dynamic counter identifiers like terminalId into safe low-cardinality labels.
     */
    public static String sanitizeBoundedLabel(String raw, String defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        String cleaned = ALPHANUM_LABEL_PATTERN.matcher(raw.trim().toUpperCase(Locale.ROOT)).replaceAll("_");
        if (cleaned.isBlank()) {
            return defaultValue;
        }
        return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
    }
}
