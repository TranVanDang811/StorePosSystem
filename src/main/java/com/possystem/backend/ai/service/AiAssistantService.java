package com.possystem.backend.ai.service;

import com.possystem.backend.ai.dto.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface AiAssistantService {

    AiChatResponse chat(String userId, AiChatRequest request);

    SseEmitter streamChat(String userId, AiChatRequest request);

    List<AiConversationDto> getConversations(String userId, Pageable pageable);

    List<AiMessageDto> getConversationMessages(String userId, String conversationId);

    void deleteConversation(String userId, String conversationId);

    AiUsageSummaryDto getUsageSummary(String userId);
}
