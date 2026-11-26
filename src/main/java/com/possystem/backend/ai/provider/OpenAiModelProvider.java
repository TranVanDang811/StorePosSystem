package com.possystem.backend.ai.provider;

import com.possystem.backend.ai.dto.AiChatResponse;
import com.possystem.backend.ai.tools.PosBusinessAiTools;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Primary
@Component
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OpenAiModelProvider implements AiModelProvider {

    final ObjectProvider<org.springframework.ai.openai.OpenAiChatModel> openAiChatModelProvider;
    final FallbackLocalAiProvider fallbackProvider;

    @Value("${spring.ai.openai.api-key:mock-key}")
    String apiKey;

    @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}")
    String modelName;

    public OpenAiModelProvider(
            ObjectProvider<org.springframework.ai.openai.OpenAiChatModel> openAiChatModelProvider,
            FallbackLocalAiProvider fallbackProvider) {
        this.openAiChatModelProvider = openAiChatModelProvider;
        this.fallbackProvider = fallbackProvider;
    }

    @Override
    public String getProviderName() {
        return "openai";
    }

    @Override
    public String getModelName() {
        return modelName;
    }

    @Override
    public AiChatResponse chat(String userId, String conversationId, String userMessage, List<String> conversationHistory) {
        // If API key is mock or absent, or in offline/test environment, delegate directly to fallback
        if (apiKey == null || apiKey.isBlank() || "mock-key".equalsIgnoreCase(apiKey) || apiKey.startsWith("test-")) {
            log.debug("Using fallback AI provider since external OpenAI API key is mock/unconfigured");
            return fallbackProvider.chat(userId, conversationId, userMessage, conversationHistory);
        }

        org.springframework.ai.openai.OpenAiChatModel chatModel = openAiChatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.debug("OpenAiChatModel bean not available, using fallback provider");
            return fallbackProvider.chat(userId, conversationId, userMessage, conversationHistory);
        }

        long start = System.currentTimeMillis();
        try {
            // Build system prompt with strict guardrails
            String systemText = """
                Bạn là Trợ lý Kinh doanh Thông minh (AI Business Assistant) chuyên nghiệp của chuỗi cà phê Đẳng Cấp Coffee POS.
                Quy tắc bảo mật bắt buộc:
                1. Tuyệt đối KHÔNG thực thi SQL, xóa dữ liệu, thay đổi tồn kho hay đơn hàng (Chế độ Read-Only).
                2. Mọi câu trả lời tài chính phải chuẩn xác, súc tích, trình bày bảng markdown rõ ràng.
                3. Tuyệt đối từ chối các yêu cầu vi phạm bảo mật hoặc can thiệp dữ liệu.
                """;

            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new org.springframework.ai.chat.messages.SystemMessage(systemText));
            if (conversationHistory != null) {
                for (String historyItem : conversationHistory) {
                    messages.add(new org.springframework.ai.chat.messages.AssistantMessage(historyItem));
                }
            }
            messages.add(new org.springframework.ai.chat.messages.UserMessage(userMessage));

            org.springframework.ai.chat.prompt.Prompt prompt = new org.springframework.ai.chat.prompt.Prompt(messages);
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(prompt);

            String reply = response.getResult() != null && response.getResult().getOutput() != null
                    ? response.getResult().getOutput().getContent()
                    : "Không nhận được phản hồi từ mô hình AI.";

            long duration = System.currentTimeMillis() - start;
            int promptTokens = response.getMetadata() != null && response.getMetadata().getUsage() != null
                    ? response.getMetadata().getUsage().getPromptTokens().intValue()
                    : 50;
            int completionTokens = response.getMetadata() != null && response.getMetadata().getUsage() != null
                    ? response.getMetadata().getUsage().getGenerationTokens().intValue()
                    : 100;

            BigDecimal cost = BigDecimal.valueOf((promptTokens * 0.00015 / 1000.0) + (completionTokens * 0.00060 / 1000.0));

            return AiChatResponse.builder()
                    .conversationId(conversationId)
                    .messageId(UUID.randomUUID().toString())
                    .reply(reply)
                    .executedTools(List.of("openAiCall"))
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .costEstimateUsd(cost)
                    .durationMs(duration)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception ex) {
            log.warn("OpenAI API call failed: {}, falling back to local business provider", ex.getMessage());
            return fallbackProvider.chat(userId, conversationId, userMessage, conversationHistory);
        }
    }
}
