package com.possystem.backend.common.util.mapper;

import com.possystem.backend.discount.dto.DiscountRequest;
import com.possystem.backend.discount.dto.DiscountResponse;
import com.possystem.backend.discount.entity.Discount;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface DiscountMapper {
    DiscountResponse toDiscountResponse(Discount discount);

    //    @Mapping(target = "active", ignore = true) // Bỏ qua trường active khi map
    Discount toDiscount(DiscountRequest createDiscountRequest);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDiscountFromRequest(DiscountRequest updateRequest, @MappingTarget Discount discount);
}