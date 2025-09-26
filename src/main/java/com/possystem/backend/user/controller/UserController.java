package com.possystem.backend.user.controller;

import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.user.dto.ChangePasswordRequest;
import com.possystem.backend.user.dto.UserCreationRequest;
import com.possystem.backend.user.dto.UserResponse;
import com.possystem.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Tag(name = "User API", description = "User management")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserController {
    UserService userService;

    // Create new user
    @PostMapping
    @Operation(summary = "Create new user", description = "API to create users in the system")
    ApiResponse<UserResponse> createUser(@RequestBody @Valid UserCreationRequest request) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.createUser(request))
                .build();
    }
    // Get a list of users with pagination
    @GetMapping
    @Operation(summary = "Get list users", description = "Get a list of users with pagination")
    ApiResponse<Page<UserResponse>> getUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size) {


        var authentication = SecurityContextHolder.getContext().getAuthentication();
        authentication.getAuthorities().forEach(grantedAuthority -> log.info(grantedAuthority.getAuthority()));

        return ApiResponse.<Page<UserResponse>>builder()
                .result(userService.getUsers(page - 1, size))
                .build();
    }
    // Get information of logged-in user
    @GetMapping("/myInfo")
    @Operation(summary = "Get logged-in user info", description = "Get current user information (from token)")
    ApiResponse<UserResponse> getMyInfo() {
        return ApiResponse.<UserResponse>builder()
                .result(userService.getMyInfo())
                .build();
    }

    // Search user by keyword (with pagination)
    @GetMapping("/filter")
    @Operation(summary = "Search users", description = "Search user by keyword, role, status (with pagination)")
    public ApiResponse<Page<UserResponse>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.<Page<UserResponse>>builder()
                .result(userService.searchUsers(keyword, roleName, status,page - 1, size))
                .build();
    }

    // Delete user by id
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user", description = "Delete user by ID")
    ApiResponse<Void>deleteUser(@PathVariable String userId) {
        userService.deleteUser(userId);
        return ApiResponse.<Void>builder().message("Delete successfully").build();
    }

    // Update user status (ACTIVE, INACTIVE, NONE, etc.)
    @PatchMapping("/{userId}")
    @Operation(summary = "Change user status", description = "Update user status (ACTIVE, INACTIVE, NONE, ...)")
    public ApiResponse<UserResponse> changerStatus(
            @PathVariable String userId,
            @RequestParam UserStatus status) {

        log.info("Request changer user status, userId = {}", userId);

        UserResponse response = userService.changerStatus(userId, status);

        return ApiResponse.<UserResponse>builder()
                .result(response)
                .build();
    }

    // Update user roles
    @PatchMapping("/{userId}/role")
    @Operation(summary = "Update user role", description = "Update roles for users")
    public ApiResponse<UserResponse> updateRole(
            @PathVariable String userId,
            @RequestParam String roleName) {
        return ApiResponse.<UserResponse>builder()
                .result(userService.updateRole(userId, roleName))
                .build();
    }

    // Change user password by id
    @PatchMapping("/{userId}/change-password")
    @Operation(summary = "Change password", description = "Change password for user using userId")
    public ApiResponse<Void> changePassword(
            @PathVariable String userId,
            @RequestBody ChangePasswordRequest request) {
        userService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return ApiResponse.<Void>builder()
                .message("Password updated successfully")
                .build();
    }
}
