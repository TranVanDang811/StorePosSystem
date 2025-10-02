package com.possystem.backend.user.service;

import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.user.dto.UserCreationRequest;
import com.possystem.backend.user.dto.UserResponse;
import com.possystem.backend.user.dto.UserUpdateRequest;
import org.springframework.data.domain.Page;

public interface  UserService {
    UserResponse createUser(UserCreationRequest request);
    Page<UserResponse> getUsers(int page, int size);
    UserResponse getMyInfo();
    Page<UserResponse> searchUsers(String keyword, String roleName, UserStatus status, int page, int size);
    void deleteUser(String userId);
    UserResponse changerStatus(String userId, UserStatus status);
    UserResponse updateRole(String userId, String roleName);
    void changePassword(String userId, String oldPassword, String newPassword);
    UserResponse updateUser(String id, UserUpdateRequest request);
}
