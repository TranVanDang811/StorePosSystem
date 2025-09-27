package com.possystem.backend.common.util.mapper;

import com.possystem.backend.product.dto.ProductRequest;
import com.possystem.backend.product.dto.ProductResponse;
import com.possystem.backend.product.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    Product toProduct(ProductRequest request);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "imageUrl", source = "imageUrl")
    @Mapping(target = "category", source = "category")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updateAt", source = "updatedAt")
    ProductResponse toProductResponse(Product product);

}