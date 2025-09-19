package com.possystem.backend.user.service;

import com.possystem.backend.user.dto.UserCreationRequest;
import com.possystem.backend.user.dto.UserResponse;
import org.springframework.data.domain.Page;

public interface  UserService {
    UserResponse createUser(UserCreationRequest request);
    Page<UserResponse> getUsers(int page, int size);
}
