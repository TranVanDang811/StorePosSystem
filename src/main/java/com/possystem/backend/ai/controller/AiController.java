package com.possystem.backend.ai.controller;

import com.possystem.backend.ai.dto.*;
import com.possystem.backend.ai.service.AiAssistantService;
import com.possystem.backend.ai.tools.PosBusinessAiTools;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "AI Business Assistant", description = "AI-powered natural language business analytics and tool execution")
@PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
public class AiController {

    AiAssistantService aiAssistantService;
    PosBusinessAiTools businessTools;
    UserRepository userRepository;

    @Operation(summary = "Send a message to the AI Assistant (Synchronous)")
    @PostMapping("/chat")
    public ApiResponse<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        String userId = resolveCurrentUserId();
        AiChatResponse response = aiAssistantService.chat(userId, request);
        return ApiResponse.<AiChatResponse>builder()
                .result(response)
                .build();
    }

    @Operation(summary = "Stream AI response via Server-Sent Events (SSE)")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@Valid @RequestBody AiChatRequest request) {
        String userId = resolveCurrentUserId();
        return aiAssistantService.streamChat(userId, request);
    }

    @Operation(summary = "Get list of conversations for current user")
    @GetMapping("/conversations")
    public ApiResponse<List<AiConversationDto>> getConversations(Pageable pageable) {
        String userId = resolveCurrentUserId();
        List<AiConversationDto> list = aiAssistantService.getConversations(userId, pageable);
        return ApiResponse.<List<AiConversationDto>>builder()
                .result(list)
                .build();
    }

    @Operation(summary = "Get message history for a conversation")
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<AiMessageDto>> getConversationMessages(@PathVariable String id) {
        String userId = resolveCurrentUserId();
        List<AiMessageDto> messages = aiAssistantService.getConversationMessages(userId, id);
        return ApiResponse.<List<AiMessageDto>>builder()
                .result(messages)
                .build();
    }

    @Operation(summary = "Delete or archive a conversation")
    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(@PathVariable String id) {
        String userId = resolveCurrentUserId();
        aiAssistantService.deleteConversation(userId, id);
        return ApiResponse.<Void>builder()
                .message("Cuộc trò chuyện đã được xóa thành công")
                .build();
    }

    @Operation(summary = "Get AI token and cost usage summary")
    @GetMapping("/usage")
    public ApiResponse<AiUsageSummaryDto> getUsageSummary() {
        String userId = resolveCurrentUserId();
        AiUsageSummaryDto summary = aiAssistantService.getUsageSummary(userId);
        return ApiResponse.<AiUsageSummaryDto>builder()
                .result(summary)
                .build();
    }

    // --- Direct Bounded Read-Only Tools (For transparency, debugging & direct dashboard widgets) ---

    @Operation(summary = "Tool: Get Sales Summary")
    @GetMapping("/tools/sales-summary")
    public ApiResponse<SalesSummaryDto> getSalesSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.<SalesSummaryDto>builder()
                .result(businessTools.getSalesSummary(fromDate, toDate))
                .build();
    }

    @Operation(summary = "Tool: Get Top Selling Products")
    @GetMapping("/tools/top-products")
    public ApiResponse<TopSellingProductsDto> getTopSellingProducts(
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.<TopSellingProductsDto>builder()
                .result(businessTools.getTopSellingProducts(limit, fromDate, toDate))
                .build();
    }

    @Operation(summary = "Tool: Get Inventory Status & Low Stock Alert")
    @GetMapping("/tools/inventory-status")
    public ApiResponse<InventoryStatusDto> getInventoryStatus(
            @RequestParam(required = false) Integer threshold) {
        return ApiResponse.<InventoryStatusDto>builder()
                .result(businessTools.getInventoryStatus(threshold))
                .build();
    }

    @Operation(summary = "Tool: Get Current Shift Revenue")
    @GetMapping("/tools/shift-revenue")
    public ApiResponse<ShiftRevenueDto> getShiftRevenue(
            @RequestParam(required = false) String terminalId,
            @RequestParam(required = false) String shiftCode) {
        return ApiResponse.<ShiftRevenueDto>builder()
                .result(businessTools.getShiftRevenue(terminalId, shiftCode))
                .build();
    }

    @Operation(summary = "Tool: Compare Revenue between two periods")
    @GetMapping("/tools/revenue-comparison")
    public ApiResponse<RevenueComparisonDto> compareRevenue(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1Start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period1End,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2Start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate period2End) {
        return ApiResponse.<RevenueComparisonDto>builder()
                .result(businessTools.compareRevenue(period1Start, period1End, period2Start, period2End))
                .build();
    }

    @Operation(summary = "Tool: Get Product Profitability Ranking")
    @GetMapping("/tools/profitability")
    public ApiResponse<ProfitabilityReportDto> getProfitability(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.<ProfitabilityReportDto>builder()
                .result(businessTools.getProfitabilityReport(limit))
                .build();
    }

    private String resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseGet(() -> userRepository.findById(username)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND)));
        return user.getId();
    }
}
