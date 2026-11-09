package com.possystem.backend.user.service;

import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.user.dto.UserCreationRequest;
import com.possystem.backend.user.dto.UserResponse;
import com.possystem.backend.user.dto.UserUpdateRequest;
import com.possystem.backend.user.entity.User;
import org.springframework.data.domain.Page;

import java.util.List;

public interface UserService {
    UserResponse createUser(UserCreationRequest request);
    UserResponse getMyInfo();
    Page<UserResponse> searchUsers(String keyword, String roleName, UserStatus status, int page, int size);
    Page<UserResponse> searchUsers(String keyword, String roleName, List<String> roleNames, UserStatus status, int page, int size);
    void deleteUser(String userId);
    UserResponse changerStatus(String userId, UserStatus status);
    UserResponse updateRole(String userId, String roleName);
    void changePassword(String userId, String oldPassword, String newPassword);
    UserResponse updateUser(String id, UserUpdateRequest request);
    UserResponse getUserById(String userId);
    UserResponse getCustomerByPhone(String phone);
    User getCurrentUser();
    Page<UserResponse> getUsers(List<String> roleNames, int page, int size);
}
