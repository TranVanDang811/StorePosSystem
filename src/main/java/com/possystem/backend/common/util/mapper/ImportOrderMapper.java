package com.possystem.backend.common.util.mapper;

import com.possystem.backend.importorder.dto.ImportOrderCreateRequest;
import com.possystem.backend.importorder.dto.ImportOrderDetailResponse;
import com.possystem.backend.importorder.dto.ImportOrderResponse;
import com.possystem.backend.importorder.entity.ImportOrder;
import com.possystem.backend.importorder.entity.ImportOrderDetail;
import org.mapstruct.*;

import java.util.List;


@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface ImportOrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "importDate", expression = "java(java.time.LocalDateTime.now())")
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "totalQuantity", ignore = true)
    @Mapping(target = "supplier", ignore = true)
    @Mapping(target = "importDetails", ignore = true)
    @Mapping(target = "confirmStatus", ignore = true)
    ImportOrder toEntity(ImportOrderCreateRequest request);


    @Mapping(target = "totalReceivedQuantity",
            expression = "java(order.getTotalReceivedQuantity())")
    @Mapping(target = "supplierId", source = "supplier.id")
    @Mapping(target = "supplierName", source = "supplier.name")
    @Mapping(target = "importDetails", source = "importDetails")
    ImportOrderResponse toResponse(ImportOrder order);

    List<ImportOrderResponse> toResponseList(List<ImportOrder> orders);


    // ===== DETAIL MAPPING =====

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "lineTotal",
            expression = "java(detail.getImportPrice().multiply(java.math.BigDecimal.valueOf(detail.getQuantity())))")
    ImportOrderDetailResponse toDetailResponse(ImportOrderDetail detail);

    List<ImportOrderDetailResponse> toDetailResponseList(List<ImportOrderDetail> details);
}