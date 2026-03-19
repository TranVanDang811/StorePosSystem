package com.possystem.backend.common.util.mapper;

import com.possystem.backend.order.dto.OrderDetailResponse;
import com.possystem.backend.order.entity.OrderDetail;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderDetailMapper {

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productCode", source = "product.productCode")
    @Mapping(target = "name", source = "product.name")
    @Mapping(target = "price", source = "product.price")
    @Mapping(target = "quantity", source = "quantity")
    @Mapping(target = "totalPrice", source = "totalPrice")
    OrderDetailResponse toResponse(OrderDetail orderDetail);
}