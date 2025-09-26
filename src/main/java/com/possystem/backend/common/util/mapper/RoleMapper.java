package com.possystem.backend.common.util.mapper;

import com.possystem.backend.role.dto.RoleRequest;
import com.possystem.backend.role.dto.RoleResponse;
import com.possystem.backend.role.entity.Role;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface RoleMapper {

    Role toRole(RoleRequest request);

    RoleResponse toRoleResponse(Role role);
}
