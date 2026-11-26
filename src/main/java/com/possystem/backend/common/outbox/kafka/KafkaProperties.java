package com.possystem.backend.common.outbox.kafka;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.kafka")
public class KafkaProperties {
    private boolean enabled = false;
    private String bootstrapServers = "localhost:9092";
    private String consumerGroup = "pos-monolith-workers";
    private long sendTimeoutMs = 5000;
    private int retryAttempts = 3;
    private long retryInitialDelayMs = 2000;
    private double retryMultiplier = 2.0;
    private long retryMaxDelayMs = 30000;
    private Topics topics = new Topics();

    @Data
    public static class Topics {
        private String orders = "pos.orders.events";
        private String payments = "pos.payments.events";
        private String inventory = "pos.inventory.events";
        private String loyalty = "pos.loyalty.events";
        private String vouchers = "pos.vouchers.events";
        private String shifts = "pos.shifts.events";
        private int partitions = 3;
        private short replicas = 1;
    }
}
