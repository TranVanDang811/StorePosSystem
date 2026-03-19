package com.possystem.backend.common.util.mapper;

import com.possystem.backend.order.dto.OrderResponse;
import com.possystem.backend.order.entity.Orders;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {OrderDetailMapper.class})
public interface OrderMapper {
    @Mapping(target = "orderCode", source = "orderCode")

       @Mapping(target = "orderDetails", source = "orderDetails")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "discountCode", source = "discountCode")

    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    OrderResponse toResponse(Orders order);


}