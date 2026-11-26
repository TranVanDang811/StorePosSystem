package com.possystem.backend.ai.provider;

import com.possystem.backend.ai.dto.AiChatResponse;

import java.util.List;

public interface AiModelProvider {
    String getProviderName();
    String getModelName();

    /**
     * Synchronous chat completion with tool calling capability and system guardrails.
     */
    AiChatResponse chat(String userId, String conversationId, String userMessage, List<String> conversationHistory);
}
