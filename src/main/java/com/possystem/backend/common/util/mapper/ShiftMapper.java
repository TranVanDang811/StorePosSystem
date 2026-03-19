package com.possystem.backend.common.util.mapper;

import com.possystem.backend.shift.dto.ShiftClosingReportResponse;
import com.possystem.backend.shift.dto.ShiftResponse;
import com.possystem.backend.shift.entity.Shift;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ShiftMapper {
    @Mapping(target = "openedByName", source = "openedBy.fullName")
    @Mapping(target = "closedByName", source = "closedBy.fullName")
    ShiftResponse toResponse(Shift shift);

    @Mapping(target = "openedByName", source = "openedBy.fullName")
    @Mapping(target = "closedByName", source = "closedBy.fullName")
    @Mapping(target = "closedById", source = "closedBy.id")
    @Mapping(target = "totalOrdersPaid", source = "totalOrders")
    ShiftClosingReportResponse toClosingReportResponse(Shift shift);
}