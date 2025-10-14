package com.possystem.backend.discount.service.impl;

import com.possystem.backend.common.enums.DiscountType;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.DiscountMapper;
import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.dto.DiscountResponse;
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

    @Scheduled(cron = "0 0 0 * * *")
    public void updateDiscountStatusAutomatically() {
        LocalDateTime now = LocalDateTime.now();
        log.info("-->Checking discount status at {}", now);

        // 🔹 Lấy các discount còn hiệu lực hoặc mới hết hạn
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

    public DiscountResponse createDiscount(DiscountRequest request) {
        Discount discount = new Discount();

        discount.setName(request.getName());
        discount.setStartDate(request.getStartDate());
        discount.setEndDate(request.getEndDate());
        discount.setCode(request.getCode());


        discount.setActive(isActive(discount.getStartDate(), discount.getEndDate()));

        log.info("Active status before save: {}", discount.isActive());
        Discount savedDiscount = discountRepository.save(discount);
        log.info("Active status after save: {}", savedDiscount.isActive());

        return discountMapper.toDiscountResponse(savedDiscount);
    }

    public DiscountResponse updateDiscount(String id, DiscountRequest request) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE,id));

        discountMapper.updateDiscountFromRequest(request, discount);


        discount.setActive(isActive(discount.getStartDate(), discount.getEndDate()));

        Discount updatedDiscount = discountRepository.save(discount);

        return discountMapper.toDiscountResponse(updatedDiscount);
    }



    public void deleteDiscount(String id) {
        if (!discountRepository.existsById(id)) {
            throw new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE,id);
        }
        discountRepository.deleteById(id);
    }

    public DiscountResponse getDiscountById(String id) {
        Discount discount = discountRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND_MESSAGE,id));
        return discountMapper.toDiscountResponse(discount);
    }

    public List<DiscountResponse> getAllDiscounts() {
        return discountRepository.findAll()
                .stream()
                .map(discountMapper::toDiscountResponse)
                .toList(); // Replaced collect with toList()
    }

    public List<DiscountResponse> getActiveDiscounts() {
        return discountRepository.findAll()
                .stream()
                .filter(Discount::isActive)
                .map(discountMapper::toDiscountResponse)
                .toList();
    }

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

    @Transactional
    public BigDecimal applyDiscount(Orders order, String discountCode, Integer usedPoints) {
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal pointDiscount = BigDecimal.ZERO;

        // ✅ 1. Áp dụng mã giảm giá (nếu có)
        if (discountCode != null && !discountCode.isBlank()) {
            Discount discount = discountRepository.findByCode(discountCode)
                    .orElseThrow(() -> new AppException(ErrorCode.DISCOUNT_NOT_FOUND, discountCode));

            // 🔍 Kiểm tra hạn sử dụng
            LocalDateTime now = LocalDateTime.now();
            if (discount.getStartDate() != null && now.isBefore(discount.getStartDate())
                    || discount.getEndDate() != null && now.isAfter(discount.getEndDate())) {
                throw new AppException(ErrorCode.INVALID_DISCOUNT_CODE);
            }

            // 💰 Tính tiền giảm
            if (discount.getDiscountType() == DiscountType.PERCENT) {
                discountAmount = order.getTotalPrice()
                        .multiply(discount.getValue().divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
            } else {
                discountAmount = discount.getValue();
            }

            // ✅ Giới hạn không vượt quá tổng tiền
            if (discountAmount.compareTo(order.getTotalPrice()) > 0) {
                discountAmount = order.getTotalPrice();
            }

            order.setDiscountCode(discountCode);
            order.setDiscountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP));
        } else {
            order.setDiscountAmount(BigDecimal.ZERO);
        }

        // ✅ 2. Áp dụng điểm khách hàng (nếu có)
        if (usedPoints != null && usedPoints > 0) {
            CustomerProfile profile = order.getUser().getCustomerProfile();
            if (profile == null) {
                throw new AppException(ErrorCode.USER_NOT_FOUND);
            }

            if (profile.getLoyaltyPoints() < usedPoints) {
                throw new AppException(ErrorCode.NOT_ENOUGH_POINTS);
            }

            // 🔻 Trừ điểm của khách hàng
            profile.setLoyaltyPoints(profile.getLoyaltyPoints() - usedPoints);

            // 💰 1000 điểm = 1000đ
            pointDiscount = BigDecimal.valueOf(usedPoints);
            order.setUsedPoints(usedPoints);
            order.setPointDiscount(pointDiscount.setScale(2, RoundingMode.HALF_UP));
        } else {
            order.setUsedPoints(0);
            order.setPointDiscount(BigDecimal.ZERO);
        }

        // ✅ 3. Cập nhật tổng tiền cuối cùng
        BigDecimal finalAmount = order.getTotalPrice()
                .subtract(order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO)
                .subtract(order.getPointDiscount() != null ? order.getPointDiscount() : BigDecimal.ZERO);

        if (finalAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalAmount = BigDecimal.ZERO; // không cho âm tiền
        }

        order.setFinalAmount(finalAmount.setScale(2, RoundingMode.HALF_UP));

        // ✅ 4. Lưu lại profile (vì có trừ điểm)
        customerProfileRepository.save(order.getUser().getCustomerProfile());

        return finalAmount;
    }


}
