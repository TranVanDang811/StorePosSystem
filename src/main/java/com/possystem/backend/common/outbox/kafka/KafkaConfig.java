package com.possystem.backend.common.outbox.kafka;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Kafka configuration enabled only when app.kafka.enabled=true.
 * Registers partitioned domain topics, idempotent acks=all producer, and consumer container factory.
 */
@EnableKafka
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        return new KafkaAdmin(configs);
    }

    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        int timeoutMs = (int) Math.max(1000L, kafkaProperties.getSendTimeoutMs());
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, timeoutMs);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, Math.max(timeoutMs + 1000, timeoutMs * 2));
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        MeterRegistry registry = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;
        if (registry != null) {
            factory.addListener(new MicrometerProducerListener<>(registry));
        }
        return factory;
    }

    @Bean(name = "stringKafkaTemplate")
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.getConsumerGroup());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        DefaultKafkaConsumerFactory<String, String> factory = new DefaultKafkaConsumerFactory<>(props);
        MeterRegistry registry = meterRegistryProvider != null ? meterRegistryProvider.getIfAvailable() : null;
        if (registry != null) {
            factory.addListener(new MicrometerConsumerListener<>(registry));
        }
        return factory;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    @Bean
    public NewTopic posOrdersTopic() {
        return buildTopic(kafkaProperties.getTopics().getOrders());
    }

    @Bean
    public NewTopic posPaymentsTopic() {
        return buildTopic(kafkaProperties.getTopics().getPayments());
    }

    @Bean
    public NewTopic posInventoryTopic() {
        return buildTopic(kafkaProperties.getTopics().getInventory());
    }

    @Bean
    public NewTopic posLoyaltyTopic() {
        return buildTopic(kafkaProperties.getTopics().getLoyalty());
    }

    @Bean
    public NewTopic posVouchersTopic() {
        return buildTopic(kafkaProperties.getTopics().getVouchers());
    }

    @Bean
    public NewTopic posShiftsTopic() {
        return buildTopic(kafkaProperties.getTopics().getShifts());
    }

    private NewTopic buildTopic(String topicName) {
        KafkaProperties.Topics topics = kafkaProperties.getTopics();
        return TopicBuilder.name(topicName)
                .partitions(Math.max(1, topics.getPartitions()))
                .replicas(Math.max(1, topics.getReplicas()))
                .build();
    }
}
