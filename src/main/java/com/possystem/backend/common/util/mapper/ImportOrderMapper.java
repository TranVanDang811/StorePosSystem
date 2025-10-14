package com.possystem.backend.common.util.mapper;

import com.possystem.backend.importorder.dto.ImportOrderCreateRequest;
import com.possystem.backend.importorder.dto.ImportOrderDetailResponse;
import com.possystem.backend.importorder.dto.ImportOrderResponse;
import com.possystem.backend.importorder.entity.ImportOrder;
import com.possystem.backend.importorder.entity.ImportOrderDetail;
import org.mapstruct.*;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ImportOrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "importDate", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "status", constant = "NOT_CONFIRMED")
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "totalQuantity", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "importDetails", ignore = true)
    ImportOrder toEntity(ImportOrderCreateRequest request);

    // Mapping đơn nhập sang DTO
    @Mapping(target = "supplierId", source = "supplier.id", qualifiedByName = "mapUuidToString")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "importDetails", ignore = true) // custom bằng @AfterMapping
    ImportOrderResponse toResponse(ImportOrder order);

    List<ImportOrderResponse> toResponseList(List<ImportOrder> orders);

    // Mapping chi tiết đơn nhập
    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    ImportOrderDetailResponse toDetailDto(ImportOrderDetail detail);

    List<ImportOrderDetailResponse> toDetailDtoList(List<ImportOrderDetail> details);

    // Custom sau khi ánh xạ xong
    @AfterMapping
    default void afterMapping(@MappingTarget ImportOrderResponse response, ImportOrder order) {
        response.setImportDetails(toDetailDtoList(order.getImportDetails()));
    }
    @Named("mapUuidToString")
    default String mapUuidToString(UUID uuid) {
        return uuid != null ? uuid.toString() : null;
    }
}
