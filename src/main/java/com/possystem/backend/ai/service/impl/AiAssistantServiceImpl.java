package com.possystem.backend.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.ai.dto.*;
import com.possystem.backend.ai.entity.AiConversation;
import com.possystem.backend.ai.entity.AiMessage;
import com.possystem.backend.ai.entity.AiUsageLog;
import com.possystem.backend.ai.provider.AiModelProvider;
import com.possystem.backend.ai.repository.AiConversationRepository;
import com.possystem.backend.ai.repository.AiMessageRepository;
import com.possystem.backend.ai.repository.AiUsageLogRepository;
import com.possystem.backend.ai.service.AiAssistantService;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AiAssistantServiceImpl implements AiAssistantService {

    final AiConversationRepository conversationRepository;
    final AiMessageRepository messageRepository;
    final AiUsageLogRepository usageLogRepository;
    final AiModelProvider aiModelProvider;
    final ObjectMapper objectMapper;

    @Value("${app.ai.daily-token-limit:50000}")
    long dailyTokenLimit = 50000;

    @Value("${app.ai.monthly-token-limit:500000}")
    long monthlyTokenLimit = 500000;

    @Override
    @Transactional
    public AiChatResponse chat(String userId, AiChatRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Quota check
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        long tokensUsedToday = usageLogRepository.sumTotalTokensByUserIdSince(userId, startOfDay);
        if (tokensUsedToday >= dailyTokenLimit) {
            log.warn("User {} exceeded daily AI token limit: {}/{}", userId, tokensUsedToday, dailyTokenLimit);
            return buildQuotaExceededResponse(request.getConversationId(), userId, startTime);
        }

        // 2. Resolve or create Conversation
        AiConversation conversation = resolveConversation(userId, request.getConversationId(), request.getMessage());

        // 3. Save User Message
        AiMessage userMsg = AiMessage.builder()
                .conversation(conversation)
                .role("USER")
                .content(request.getMessage())
                .build();
        messageRepository.save(userMsg);

        // 4. Load recent conversation history (last 8 messages)
        List<AiMessage> pastMessages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversation.getId());
        List<String> history = new ArrayList<>();
        int startIndex = Math.max(0, pastMessages.size() - 8);
        for (int i = startIndex; i < pastMessages.size() - 1; i++) {
            AiMessage m = pastMessages.get(i);
            history.add(m.getRole() + ": " + m.getContent());
        }

        // 5. Invoke AI Provider
        AiChatResponse response = aiModelProvider.chat(userId, conversation.getId(), request.getMessage(), history);

        // 6. Save Assistant Message
        String toolJson = null;
        try {
            if (response.getExecutedTools() != null && !response.getExecutedTools().isEmpty()) {
                toolJson = objectMapper.writeValueAsString(response.getExecutedTools());
            }
        } catch (Exception ignored) {}

        AiMessage assistantMsg = AiMessage.builder()
                .conversation(conversation)
                .role("ASSISTANT")
                .content(response.getReply())
                .toolCallsJson(toolJson)
                .build();
        messageRepository.save(assistantMsg);

        // 7. Record AI Usage Log
        AiUsageLog usageLog = AiUsageLog.builder()
                .userId(userId)
                .conversationId(conversation.getId())
                .provider(aiModelProvider.getProviderName())
                .model(aiModelProvider.getModelName())
                .promptTokens(response.getPromptTokens())
                .completionTokens(response.getCompletionTokens())
                .totalTokens(response.getTotalTokens())
                .durationMs(response.getDurationMs())
                .costEstimateUsd(response.getCostEstimateUsd())
                .status("SUCCESS")
                .build();
        usageLogRepository.save(usageLog);

        // Update conversation timestamp
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        response.setConversationId(conversation.getId());
        response.setMessageId(assistantMsg.getId());
        return response;
    }

    @Override
    public SseEmitter streamChat(String userId, AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(90_000L); // 90 seconds timeout

        CompletableFuture.runAsync(() -> {
            try {
                // Execute standard chat and obtain response
                AiChatResponse response = this.chat(userId, request);

                // Stream content in words/tokens
                String fullReply = response.getReply();
                String[] words = fullReply.split("(?<=\\s)|(?<=\\n)");

                for (String word : words) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(word));
                    Thread.sleep(15); // Smooth streaming pacing
                }

                // Send metadata event
                Map<String, Object> meta = new HashMap<>();
                meta.put("conversationId", response.getConversationId());
                meta.put("messageId", response.getMessageId());
                meta.put("executedTools", response.getExecutedTools());
                meta.put("totalTokens", response.getTotalTokens());
                meta.put("costEstimateUsd", response.getCostEstimateUsd());
                meta.put("durationMs", response.getDurationMs());

                emitter.send(SseEmitter.event()
                        .name("meta")
                        .data(objectMapper.writeValueAsString(meta)));

                // End stream
                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                emitter.complete();

            } catch (Exception ex) {
                log.error("Error streaming AI chat response", ex);
                try {
                    emitter.send(SseEmitter.event().name("error").data("Lỗi xử lý phản hồi AI: " + ex.getMessage()));
                } catch (Exception ignored) {}
                emitter.completeWithError(ex);
            }
        });

        return emitter;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiConversationDto> getConversations(String userId, Pageable pageable) {
        Pageable p = pageable != null ? pageable : PageRequest.of(0, 20);
        List<AiConversation> list = conversationRepository.findByUserIdAndArchivedFalseOrderByCreatedAtDesc(userId);
        List<AiConversationDto> dtoList = new ArrayList<>();

        for (AiConversation c : list) {
            long count = messageRepository.countByConversation_Id(c.getId());
            dtoList.add(AiConversationDto.builder()
                    .id(c.getId())
                    .title(c.getTitle())
                    .createdAt(c.getCreatedAt())
                    .updatedAt(c.getUpdatedAt())
                    .messageCount((int) count)
                    .build());
        }
        return dtoList;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiMessageDto> getConversationMessages(String userId, String conversationId) {
        AiConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!conversation.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        List<AiMessage> messages = messageRepository.findByConversation_IdOrderByCreatedAtAsc(conversationId);
        List<AiMessageDto> dtoList = new ArrayList<>();
        for (AiMessage m : messages) {
            dtoList.add(AiMessageDto.builder()
                    .id(m.getId())
                    .conversationId(conversationId)
                    .role(m.getRole())
                    .content(m.getContent())
                    .toolCallsJson(m.getToolCallsJson())
                    .createdAt(m.getCreatedAt())
                    .build());
        }
        return dtoList;
    }

    @Override
    @Transactional
    public void deleteConversation(String userId, String conversationId) {
        AiConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!conversation.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        conversation.setArchived(true);
        conversationRepository.save(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public AiUsageSummaryDto getUsageSummary(String userId) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        long todayTokens = usageLogRepository.sumTotalTokensByUserIdSince(userId, startOfDay);
        long monthTokens = usageLogRepository.sumTotalTokensByUserIdSince(userId, startOfMonth);

        BigDecimal todayCost = usageLogRepository.sumCostByUserIdSince(userId, startOfDay);
        BigDecimal monthCost = usageLogRepository.sumCostByUserIdSince(userId, startOfMonth);

        return AiUsageSummaryDto.builder()
                .todayTokens(todayTokens)
                .monthTokens(monthTokens)
                .todayCostUsd(todayCost)
                .monthCostUsd(monthCost)
                .dailyLimit(dailyTokenLimit)
                .monthlyLimit(monthlyTokenLimit)
                .quotaExceeded(todayTokens >= dailyTokenLimit || monthTokens >= monthlyTokenLimit)
                .build();
    }

    private AiConversation resolveConversation(String userId, String conversationId, String firstMessage) {
        if (conversationId != null && !conversationId.isBlank()) {
            return conversationRepository.findByIdAndUserId(conversationId, userId)
                    .orElseGet(() -> createNewConversation(userId, firstMessage));
        }
        return createNewConversation(userId, firstMessage);
    }

    private AiConversation createNewConversation(String userId, String firstMessage) {
        String title = firstMessage != null && !firstMessage.isBlank()
                ? firstMessage.trim().replaceAll("\\s+", " ")
                : "Hội thoại kinh doanh mới";
        if (title.length() > 40) {
            title = title.substring(0, 37) + "...";
        }
        AiConversation conv = AiConversation.builder()
                .userId(userId)
                .title(title)
                .build();
        return conversationRepository.save(conv);
    }

    private AiChatResponse buildQuotaExceededResponse(String conversationId, String userId, long startTime) {
        String msg = "⚠️ **Thông báo giới hạn định mức:**\n\n"
                + "Tài khoản của bạn đã đạt giới hạn sử dụng AI tối đa trong ngày ("
                + String.format("%,d", dailyTokenLimit) + " tokens).\n\n"
                + "Vui lòng quay lại vào ngày mai hoặc liên hệ Quản trị viên hệ thống để nâng mức sử dụng.";

        AiUsageLog logEntry = AiUsageLog.builder()
                .userId(userId)
                .conversationId(conversationId)
                .provider(aiModelProvider.getProviderName())
                .model(aiModelProvider.getModelName())
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .durationMs(System.currentTimeMillis() - startTime)
                .costEstimateUsd(BigDecimal.ZERO)
                .status("QUOTA_EXCEEDED")
                .errorMessage("Daily token limit reached")
                .build();
        usageLogRepository.save(logEntry);

        return AiChatResponse.builder()
                .conversationId(conversationId)
                .messageId(UUID.randomUUID().toString())
                .reply(msg)
                .executedTools(List.of())
                .promptTokens(0)
                .completionTokens(0)
                .totalTokens(0)
                .costEstimateUsd(BigDecimal.ZERO)
                .durationMs(System.currentTimeMillis() - startTime)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
