package com.possystem.backend.common.ratelimit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed configuration properties for multi-tier Redis Token Bucket rate limiting.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    private boolean redisEnabled = true;
    private int authPerMinute = 30;
    private int voucherPerMinute = 15;
    private int checkoutPerMinute = 60;
    private int reportPerMinute = 30;
    private int apiPerMinute = 240;
}
