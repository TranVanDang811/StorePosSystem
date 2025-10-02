package com.possystem.backend.common.util.mapper;

import com.possystem.backend.user.dto.CustomerProfileRequest;
import com.possystem.backend.user.entity.CustomerProfile;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface CustomerProfileMapper {
    void updateCustomerProfileFromDto(CustomerProfileRequest dto, @MappingTarget CustomerProfile entity);
}
