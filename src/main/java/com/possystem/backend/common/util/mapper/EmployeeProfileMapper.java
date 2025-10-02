package com.possystem.backend.common.util.mapper;

import com.possystem.backend.user.dto.EmployeeProfileRequest;
import com.possystem.backend.user.entity.EmployeeProfile;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface EmployeeProfileMapper {
    void updateEmployeeProfileFromDto(EmployeeProfileRequest dto, @MappingTarget EmployeeProfile entity);
}

