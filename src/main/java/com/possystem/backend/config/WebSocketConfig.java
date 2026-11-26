package com.possystem.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Topic dùng cho broadcast (pub/sub), queue dùng cho tin nhắn nhắm đích danh người dùng
        config.enableSimpleBroker("/topic", "/queue");
        // Prefix cho các message gửi từ client lên server
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Điểm kết nối STOMP hỗ trợ cả kết nối WebSocket thuần và SockJS fallback
        registry.addEndpoint("/ws/pos")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        registry.addEndpoint("/ws/pos")
                .setAllowedOriginPatterns("*");
    }
}
