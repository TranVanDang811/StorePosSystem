package com.possystem.backend.common.util.mapper;

import com.possystem.backend.product.dto.ProductImageRequest;
import com.possystem.backend.product.dto.ProductImageResponse;
import com.possystem.backend.product.entity.ProductImage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductImageMapper {

    @Mapping(target = "productId", source = "product.id")
    ProductImageResponse toResponse(ProductImage productImage);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "product", ignore = true) // xử lý thủ công trong service
    ProductImage toEntity(ProductImageRequest request);
}

