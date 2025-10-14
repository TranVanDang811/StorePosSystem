package com.possystem.backend.discount.controller;

import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.dto.DiscountResponse;
import com.possystem.backend.discount.service.DiscountService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/discounts")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Discount Management", description = "APIs for managing discounts and coupon codes")
public class DiscountController {
    final DiscountService discountService;

    //Created
    @Operation(summary = "Create a new discount",
            description = "Create a new discount or coupon code with percentage, validity, and code.")
    @PostMapping
    public ApiResponse<DiscountResponse> createDiscount(@RequestBody DiscountRequest request) {
        return ApiResponse.<DiscountResponse>builder()
                .result(discountService.createDiscount(request))
                .build();
    }

    //Update discount
    @Operation(summary = "Update an existing discount",
            description = "Update discount details such as percentage, expiration date, or status by discount ID.")
    @PutMapping("/{id}")
    public ApiResponse<DiscountResponse> updateDiscount(@PathVariable String id,
                                                        @RequestBody DiscountRequest request) {
        return ApiResponse.<DiscountResponse>builder()
                .result(discountService.updateDiscount(id, request))
                .build();
    }

    //Delete discount
    @Operation(summary = "Delete a discount",
            description = "Delete a discount permanently by its ID.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDiscount(@PathVariable String id) {
        discountService.deleteDiscount(id);
        return ApiResponse.<Void>builder().message("Delete successfully").build();
    }

    //Get discount by id
    @Operation(summary = "Get discount by ID",
            description = "Retrieve a discount’s full details using its unique ID.")
    @GetMapping("/{id}")
    public ApiResponse<DiscountResponse> getDiscountById(@PathVariable String id) {
        return ApiResponse.<DiscountResponse>builder()
                .result(discountService.getDiscountById(id))
                .build();
    }

    //Get all discounts
    @Operation(summary = "Get all discounts",
            description = "Retrieve all available discounts in the system, including active and inactive ones.")
    @GetMapping
    public ApiResponse<List<DiscountResponse>> getAllDiscounts() {
        return ApiResponse.<List<DiscountResponse>>builder()
                .result(discountService.getAllDiscounts())
                .build();
    }

    // Get a list of active coupon codes
    @Operation(summary = "Get all active discounts",
            description = "Retrieve all currently active discounts or coupon codes.")
    @GetMapping("/active")
    public ApiResponse<List<DiscountResponse>> getActiveDiscounts() {
        return ApiResponse.<List<DiscountResponse>>builder()
                .result(discountService.getActiveDiscounts())
                .build();
    }

    // Find discount code by code
    @Operation(summary = "Find discount by code",
            description = "Find a discount by its unique coupon code.")
    @GetMapping("/code/{code}")
    public ApiResponse<DiscountResponse> getByCode(@PathVariable String code) {
        return ApiResponse.<DiscountResponse>builder()
                .result(discountService.getByCode(code))
                .build();
    }
}
