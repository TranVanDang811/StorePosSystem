package com.possystem.backend.discount.service.impl;


import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.DiscountMapper;
import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.dto.DiscountResponse;
import com.possystem.backend.discount.dto.PointDiscountResult;
import com.possystem.backend.discount.entity.Discount;
import com.possystem.backend.discount.repository.DiscountRepository;
import com.possystem.backend.discount.service.DiscountService;
import com.possystem.backend.order.entity.Orders;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import jakarta.transaction.Transactional;
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
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DiscountServiceImpl implements DiscountService{
    final DiscountRepository discountRepository;
    final DiscountMapper discountMapper;
    CustomerProfileRepository customerProfileRepository;


    private boolean isActive(LocalDateTime startDate, LocalDateTime endDate) {
        LocalDateTime now = LocalDateTime.now();
        return startDate != null
                && endDate != null
                && (now.isAfter(startDate) || now.isEqual(startDate))
                && (now.isBefore(endDate) || now.isEqual(endDate));
    }

    @PreAuthorize("hasRole('ADMIN')")
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
    }
    @PreAuthorize("hasRole('ADMIN')")
    public DiscountResponse createDiscount(DiscountRequest request) {
        Discount discount = new Discount();

        discount.setName(request.getName());
        discount.setStartDate(request.getStartDate());
        discount.setEndDate(request.getEndDate());
        discount.setCode(request.getCode());
        discount.setValue(request.getValue());

        discount.setActive(isActive(discount.getStartDate(), discount.getEndDate()));

        log.info("Active status before save: {}", discount.isActive());
        Discount savedDiscount = discountRepository.save(discount);
        log.info("Active status after save: {}", savedDiscount.isActive());

        return discountMapper.toDiscountResponse(savedDiscount);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public DiscountResponse updateDiscount(String id, DiscountRequest request) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE,id));

        discountMapper.updateDiscountFromRequest(request, discount);


        discount.setActive(isActive(discount.getStartDate(), discount.getEndDate()));

        Discount updatedDiscount = discountRepository.save(discount);

        return discountMapper.toDiscountResponse(updatedDiscount);
    }


    @PreAuthorize("hasRole('ADMIN')")
    public void deleteDiscount(String id) {
        if (!discountRepository.existsById(id)) {
            throw new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE,id);
        }
        discountRepository.deleteById(id);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public DiscountResponse getDiscountById(String id) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE,id));
        return discountMapper.toDiscountResponse(discount);
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<DiscountResponse> getAllDiscounts() {
        return discountRepository.findAll()
                .stream()
                .map(discountMapper::toDiscountResponse)
                .toList(); // Replaced collect with toList()
    }
    @PreAuthorize("hasRole('ADMIN')")
    public List<DiscountResponse> getActiveDiscounts() {
        return discountRepository.findAll()
                .stream()
                .filter(Discount::isActive)
                .map(discountMapper::toDiscountResponse)
                .toList();
    }
    @PreAuthorize("hasRole('ADMIN')")
    public DiscountResponse getByCode(String code) {
        Discount discount = discountRepository.findByCode(code)
                .orElseThrow(() -> new AppException(ErrorCode.COUPON_NOT_EXISTS,code));

        // Kiểm tra ngày hết hạn
        LocalDateTime now = LocalDateTime.now();
        if (discount.getStartDate().isAfter(now) || discount.getEndDate().isBefore(now)) {
            throw new AppException(ErrorCode.COUPON_INVALID);
        }

        return discountMapper.toDiscountResponse(discount);
    }

    public BigDecimal applyDiscountCode(Orders order, String discountCode) {

        BigDecimal discountAmount;

        if (discountCode != null && !discountCode.isBlank()) {

            Discount discount = discountRepository.findByCode(discountCode)
                    .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND, discountCode));

            LocalDateTime now = LocalDateTime.now();

            if ((discount.getStartDate() != null && now.isBefore(discount.getStartDate()))
                    || (discount.getEndDate() != null && now.isAfter(discount.getEndDate()))) {
                throw new AppException(ErrorCode.INVALID_DISCOUNT_CODE);
            }

            discountAmount = discount.getValue();

            // Không giảm quá tổng tiền
            if (discountAmount.compareTo(order.getTotalPrice()) > 0) {
                discountAmount = order.getTotalPrice();
            }

            order.setDiscountCode(discountCode);
            order.setDiscountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP));

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

        // 1 point = 1đ
        BigDecimal discountAmount = BigDecimal.valueOf(requestUsedPoints)
                .multiply(BigDecimal.valueOf(1));

        int actualUsedPoints;

        // Nếu giảm vượt quá tiền đơn
        if (discountAmount.compareTo(orderAmount) > 0) {

            discountAmount = orderAmount;

            actualUsedPoints = orderAmount
                    .divide(BigDecimal.valueOf(1000))
                    .intValue();
        } else {

            actualUsedPoints = requestUsedPoints;
        }

        // Trừ điểm
        profile.setLoyaltyPoints(profile.getLoyaltyPoints() - actualUsedPoints);

        customerProfileRepository.save(profile);

        return new PointDiscountResult(discountAmount, actualUsedPoints);
    }

}
