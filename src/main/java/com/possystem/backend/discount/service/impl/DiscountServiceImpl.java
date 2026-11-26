package com.possystem.backend.discount.service.impl;

import com.possystem.backend.common.cache.CacheInvalidationService;
import com.possystem.backend.common.cache.CacheNames;
import com.possystem.backend.common.cache.PosCacheService;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.common.outbox.service.OutboxPublisher;
import com.possystem.backend.common.util.mapper.DiscountMapper;
import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.dto.DiscountResponse;
import com.possystem.backend.discount.dto.PointDiscountResult;
import com.possystem.backend.discount.entity.Discount;
import com.possystem.backend.discount.entity.DiscountUsage;
import com.possystem.backend.discount.repository.DiscountUsageRepository;
import com.possystem.backend.discount.repository.DiscountRepository;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DiscountServiceImpl implements DiscountService {
    final DiscountRepository discountRepository;
    final DiscountMapper discountMapper;
    CustomerProfileRepository customerProfileRepository;
    DiscountUsageRepository discountUsageRepository;
    PosCacheService posCacheService;
    CacheInvalidationService cacheInvalidationService;
    OutboxPublisher outboxPublisher;
    PosBusinessMetrics businessMetrics;

    private boolean isActive(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        return startDate != null
                && endDate != null
                && (now.isAfter(startDate) || now.isEqual(startDate))
                && (now.isBefore(endDate) || now.isEqual(endDate));
    }

    // Scheduled task runs as system background job, no SecurityContext
    @Scheduled(cron = "0 0 0 * * *")
    public void updateDiscountStatusAutomatically() {
        LocalDateTime now = LocalDateTime.now();
        log.info("-->Checking discount status at {}", now);

        List<Discount> discounts = discountRepository.findAllActiveOrRecentlyExpired(now.minusDays(1));

        int updatedCount = 0;
        for (Discount discount : discounts) {
            boolean shouldBeActive = isActive(discount.getStartDate(), discount.getEndDate());

            if (discount.isActive() != shouldBeActive) {
                discount.setActive(shouldBeActive);
                discountRepository.save(discount);
                updatedCount++;
                log.info("--->Update discount status [{}] -> {}", discount.getName(), shouldBeActive);
            }
        }

        log.info("---->Completed status update {} discount.", updatedCount);
        if (updatedCount > 0 && cacheInvalidationService != null) {
            cacheInvalidationService.evictDiscountCaches();
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public DiscountResponse createDiscount(DiscountRequest request) {
        Discount discount = new Discount();

        discount.setName(request.getName());
        discount.setStartDate(request.getStartDate());
        discount.setEndDate(request.getEndDate());
        discount.setCode(request.getCode());
        discount.setValue(request.getValue());
        discount.setUsageLimit(request.getUsageLimit());

        boolean timeValid = isActive(discount.getStartDate(), discount.getEndDate());
        discount.setActive(request.isActive() && timeValid);

        Discount savedDiscount = discountRepository.save(discount);
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictDiscountCaches();
        }
        return discountMapper.toDiscountResponse(savedDiscount);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public DiscountResponse updateDiscount(String id, DiscountRequest request) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE, id));

        discountMapper.updateDiscountFromRequest(request, discount);

        boolean timeValid = isActive(discount.getStartDate(), discount.getEndDate());
        discount.setActive(request.isActive() && timeValid);

        Discount updatedDiscount = discountRepository.save(discount);
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictDiscountCaches();
        }
        return discountMapper.toDiscountResponse(updatedDiscount);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public void deleteDiscount(String id) {
        if (!discountRepository.existsById(id)) {
            throw new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE, id);
        }
        discountRepository.deleteById(id);
        if (cacheInvalidationService != null) {
            cacheInvalidationService.evictDiscountCaches();
        }
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public DiscountResponse getDiscountById(String id) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE, id));
        return discountMapper.toDiscountResponse(discount);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public List<DiscountResponse> getAllDiscounts() {
        if (posCacheService != null) {
            return posCacheService.getOrLoad(CacheNames.DISCOUNTS_ALL, "all",
                    () -> new ArrayList<>(discountRepository.findAll()
                            .stream()
                            .map(discountMapper::toDiscountResponse)
                            .toList()));
        }
        return new ArrayList<>(discountRepository.findAll()
                .stream()
                .map(discountMapper::toDiscountResponse)
                .toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public List<DiscountResponse> getActiveDiscounts() {
        if (posCacheService != null) {
            return posCacheService.getOrLoad(CacheNames.DISCOUNTS_ACTIVE, "active", () -> {
                LocalDateTime now = LocalDateTime.now();
                return new ArrayList<>(discountRepository.findActiveDiscounts(now)
                        .stream()
                        .map(discountMapper::toDiscountResponse)
                        .toList());
            });
        }
        LocalDateTime now = LocalDateTime.now();
        return new ArrayList<>(discountRepository.findActiveDiscounts(now)
                .stream()
                .map(discountMapper::toDiscountResponse)
                .toList());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public DiscountResponse getByCode(String code) {
        Discount discount = discountRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.COUPON_NOT_EXISTS, code));

        // Kiểm tra ngày hết hạn
        LocalDateTime now = LocalDateTime.now();
        if (discount.getStartDate().isAfter(now) || discount.getEndDate().isBefore(now)) {
            throw new AppException(ErrorCode.COUPON_INVALID);
        }

        return discountMapper.toDiscountResponse(discount);
    }

    @Transactional
    public BigDecimal applyDiscountCode(Orders order, String discountCode) {

        BigDecimal discountAmount;

        if (discountCode != null && !discountCode.isBlank()) {

            Discount discount = discountRepository.findByCodeWithLock(discountCode.trim())
                    .or(() -> discountRepository.findByCode(discountCode.trim()))
                    .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND, discountCode));

            if (!discount.isCurrentlyValid()) {
                if (businessMetrics != null) {
                    businessMetrics.recordVoucherRedemption("FAILED", BigDecimal.ZERO);
                }
                throw new AppException(ErrorCode.INVALID_DISCOUNT_CODE,
                        "Mã giảm giá không hợp lệ hoặc đã hết lượt sử dụng");
            }

            discountAmount = discount.getValue();

            // Không giảm quá tổng tiền
            if (discountAmount.compareTo(order.getTotalPrice()) > 0) {
                discountAmount = order.getTotalPrice();
            }

            // Ghi nhận lượt sử dụng & cập nhật usedCount
            discount.incrementUsedCount();
            discountRepository.save(discount);

            if (discountUsageRepository != null && order.getId() != null) {
                if (!discountUsageRepository.existsByDiscount_IdAndOrder_Id(discount.getId(), order.getId())) {
                    DiscountUsage usage = DiscountUsage.builder()
                            .discount(discount)
                            .order(order)
                            .discountAmount(discountAmount)
                            .usedAt(LocalDateTime.now())
                            .build();
                    discountUsageRepository.save(usage);
                }
            }

            order.setDiscountCode(discountCode);
            order.setDiscountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP));

            if (outboxPublisher != null && discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                java.util.Map<String, Object> voucherPayload = new java.util.LinkedHashMap<>();
                voucherPayload.put("discountId", discount.getId());
                voucherPayload.put("discountCode", discount.getCode());
                voucherPayload.put("discountName", discount.getName());
                voucherPayload.put("orderId", order.getId());
                voucherPayload.put("orderCode", order.getOrderCode());
                voucherPayload.put("discountAmount", order.getDiscountAmount());
                voucherPayload.put("usedCount", discount.getUsedCount());
                voucherPayload.put("usageLimit", discount.getUsageLimit());
                outboxPublisher.publish(
                        "VOUCHER",
                        discount.getId(),
                        PosEventType.VOUCHER_REDEEMED,
                        "voucher:" + discount.getId(),
                        order.getOrderCode(),
                        voucherPayload);
            }

            if (cacheInvalidationService != null) {
                cacheInvalidationService.evictDiscountCaches();
            }
            if (businessMetrics != null) {
                businessMetrics.recordVoucherRedemption("SUCCESS", order.getDiscountAmount());
            }

        } else {
            order.setDiscountAmount(BigDecimal.ZERO);
        }

        return order.getDiscountAmount();
    }

    @Transactional
    public PointDiscountResult applyPointDiscount(CustomerProfile profile,
            Integer requestUsedPoints,
            BigDecimal orderAmount) {

        if (profile == null || requestUsedPoints == null || requestUsedPoints <= 0) {
            return new PointDiscountResult(BigDecimal.ZERO, 0);
        }

        if (profile.getLoyaltyPoints() < requestUsedPoints) {
            throw new AppException(ErrorCode.NOT_ENOUGH_POINTS);
        }

        // 1 point = 1 VNĐ
        final BigDecimal POINT_VALUE = BigDecimal.ONE;
        BigDecimal requestedDiscount = BigDecimal.valueOf(requestUsedPoints).multiply(POINT_VALUE);

        BigDecimal discountAmount;
        int actualUsedPoints;

        // Nếu giảm vượt quá tiền đơn
        if (requestedDiscount.compareTo(orderAmount) > 0) {
            discountAmount = orderAmount;
            actualUsedPoints = orderAmount.divide(POINT_VALUE, 0, RoundingMode.CEILING).intValue();
        } else {
            discountAmount = requestedDiscount;
            actualUsedPoints = requestUsedPoints;
        }

        // Đảm bảo không trừ âm và không trừ vượt quá điểm hiện có
        actualUsedPoints = Math.min(actualUsedPoints, profile.getLoyaltyPoints());
        actualUsedPoints = Math.max(0, actualUsedPoints);

        // Trừ điểm
        profile.setLoyaltyPoints(profile.getLoyaltyPoints() - actualUsedPoints);

        customerProfileRepository.save(profile);

        return new PointDiscountResult(discountAmount, actualUsedPoints);
    }

}
