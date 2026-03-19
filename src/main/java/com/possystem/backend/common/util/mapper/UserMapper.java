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

    @Mapping(target = "id", expression = "java(user.getId())")
    @Mapping(target = "username", expression = "java(user.getUsername() != null ? user.getUsername().trim() : null)")
    @Mapping(target = "fullName", expression = "java(user.getFullName() != null ? user.getFullName().trim() : null)")
    @Mapping(target = "email", expression = "java(user.getEmail() != null ? user.getEmail().trim() : null)")
    @Mapping(target = "phone", expression = "java(user.getPhone() != null ? user.getPhone().trim() : null)")
    @Mapping(target = "createdAt", source = "createdAt")
    @Mapping(target = "updatedAt", source = "updatedAt")
    UserResponse toUserResponse(User user);

    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "roles", ignore = true) // Bỏ qua roles khi update
    void updateUser(@MappingTarget User user, UserUpdateRequest request);
}
