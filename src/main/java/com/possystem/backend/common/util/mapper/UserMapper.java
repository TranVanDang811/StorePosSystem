package com.possystem.backend.common.util.mapper;

import com.possystem.backend.user.dto.UserCreationRequest;
import com.possystem.backend.user.dto.UserResponse;
import com.possystem.backend.user.dto.UserUpdateRequest;
import com.possystem.backend.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface UserMapper {
    @Mapping(target = "roles", ignore = true) // set trong service
    @Mapping(target = "customerProfile", source = "customerProfile")
    @Mapping(target = "employeeProfile", source = "employeeProfile")
    User toUser(UserCreationRequest request);


    UserResponse toUserResponse(User user);

    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "roles", ignore = true) // Bỏ qua roles khi update
    void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
