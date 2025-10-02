package com.possystem.backend.common.util.mapper;

import com.possystem.backend.supplier.dto.SupplierRequest;
import com.possystem.backend.supplier.dto.SupplierResponse;
import com.possystem.backend.supplier.entity.Supplier;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface SupplierMapper {

    SupplierResponse toResponse(Supplier supplier);

    Supplier toEntity(SupplierRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateSupplierFromDto(SupplierRequest request, @MappingTarget Supplier supplier);
}
