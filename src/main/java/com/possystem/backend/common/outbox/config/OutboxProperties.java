package com.possystem.backend.common.outbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.outbox")
public class OutboxProperties {
    private boolean enabled = true;
    private boolean workerEnabled = true;
    private String transport = "local";
    private String workerId = "pos-worker-1";
    private int batchSize = 30;
    private long pollIntervalMs = 2000;
    private int maxRetries = 5;
    private long baseBackoffMs = 5000;
    private long maxBackoffMs = 300000;
    private long leaseTimeoutSeconds = 60;
}
