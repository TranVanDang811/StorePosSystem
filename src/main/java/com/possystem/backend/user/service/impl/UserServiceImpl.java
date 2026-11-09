package com.possystem.backend.user.service.impl;

import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.CustomerProfileMapper;
import com.possystem.backend.common.util.mapper.EmployeeProfileMapper;
import com.possystem.backend.common.util.mapper.UserMapper;
import com.possystem.backend.role.entity.Role;
import com.possystem.backend.role.repository.RoleRepository;
import com.possystem.backend.user.dto.UserCreationRequest;
import com.possystem.backend.user.dto.UserResponse;
import com.possystem.backend.user.dto.UserUpdateRequest;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.UserRepository;
import com.possystem.backend.user.service.UserService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {
    UserMapper userMapper;
    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    CustomerProfileMapper customerProfileMapper;
    EmployeeProfileMapper employeeProfileMapper;
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public UserResponse createUser(UserCreationRequest request) {

        // Kiểm tra phân quyền để chống Privilege Escalation
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isCallerAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ADMIN") || a.getAuthority().equals("ROLE_ADMIN"));
        boolean isCallerManager = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("MANAGE") || a.getAuthority().equals("ROLE_MANAGE"));

        String requestedRole = request.getRoles();
        if (!isCallerAdmin) {
            if ("ADMIN".equalsIgnoreCase(requestedRole)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ Quản trị viên (ADMIN) mới có quyền tạo tài khoản ADMIN");
            }
            if (!isCallerManager && "MANAGE".equalsIgnoreCase(requestedRole)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Chỉ Quản trị viên (ADMIN) mới có quyền tạo tài khoản Quản lý");
            }
            if (!isCallerManager && !"CUSTOMER".equalsIgnoreCase(requestedRole)) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Nhân viên (EMPLOYEE) chỉ có quyền tạo tài khoản Khách hàng (CUSTOMER)");
            }
        }

        User user = userMapper.toUser(request);

        Role role = roleRepository.findByName(request.getRoles())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        user.setRoles(Set.of(role));

        if (role.getName().equals("CUSTOMER")) {
            // Khách hàng thì không bắt buộc username, password
            user.setUsername(null);
            user.setPassword(null);
            if (user.getEmail() != null && user.getEmail().isBlank()) {
                user.setEmail(null);
            }

            if (user.getCustomerProfile() != null) {
                user.getCustomerProfile().setUser(user);
            }
        } else {
            // Nhân viên / admin thì vẫn yêu cầu username, password, email
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                throw new AppException(ErrorCode.PASSWORD_REQUIRED);
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));

            if (user.getUsername() != null && user.getUsername().isBlank()) {
                user.setUsername(null);
            }
            if (user.getEmail() != null && user.getEmail().isBlank()) {
                user.setEmail(null);
            }

            if (user.getEmployeeProfile() != null) {
                user.getEmployeeProfile().setUser(user);
            }
        }
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.USERNAME_ALREADY_EXISTS,exception);
        }

        return userMapper.toUserResponse(user);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public Page<UserResponse> getUsers(List<String> roleNames, int page, int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users;

        if (roleNames != null && !roleNames.isEmpty()) {
            users = userRepository.findByRoles_NameIn(roleNames, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(userMapper::toUserResponse);
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.username == authentication.name")
    public UserResponse getMyInfo() {
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toUserResponse(user);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public Page<UserResponse> searchUsers(String keyword, String roleName, UserStatus status, int page, int size) {
        return searchUsers(keyword, roleName, null, status, page, size);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGE')")
    public Page<UserResponse> searchUsers(String keyword, String roleName, List<String> roleNames, UserStatus status, int page, int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = size > 0 ? size : 10;
        Pageable pageable = PageRequest.of(pageIndex, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        String kw = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;
        String rName = (roleName != null && !roleName.trim().isEmpty()) ? roleName.trim() : null;
        List<String> rNames = (roleNames != null && !roleNames.isEmpty()) ? roleNames : null;

        Page<User> users = userRepository.searchUsers(kw, rName, rNames, status, pageable);
        return users.map(userMapper::toUserResponse);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        userRepository.delete(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse changerStatus(String userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        user.setStatus(status);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse updateRole(String userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        Set<Role> newRoles = new HashSet<>();
        newRoles.add(role);
        user.setRoles(newRoles);

        return userMapper.toUserResponse(userRepository.save(user));
    }

    public void changePassword(String userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        boolean isSelf = user.getUsername() != null && user.getUsername().equals(currentUsername);
        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                .stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN") || auth.getAuthority().equals("ADMIN"));

        if (isSelf) {
            if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
                throw new AppException(ErrorCode.INVALID_PASSWORD);
            }
        } else if (!isAdmin) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @org.springframework.transaction.annotation.Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateUser(String id, UserUpdateRequest request) {
        // Tìm user theo id
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        userMapper.updateUser(user, request);

        // Cập nhật thông tin profile
        if (user.getCustomerProfile() != null && request.getCustomerProfile() != null) {
            customerProfileMapper.updateCustomerProfileFromDto(request.getCustomerProfile(), user.getCustomerProfile());
        }

        if (user.getEmployeeProfile() != null && request.getEmployeeProfile() != null) {
            employeeProfileMapper.updateEmployeeProfileFromDto(request.getEmployeeProfile(), user.getEmployeeProfile());
        }

        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, exception);
        }

        return userMapper.toUserResponse(user);
    }

    @PostAuthorize("hasRole('ADMIN') or hasRole('MANAGE') or hasRole('EMPLOYEE') or (returnObject != null && returnObject.username != null && returnObject.username == authentication.name)")
    public UserResponse getUserById(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toUserResponse(user);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE') or hasRole('MANAGE')")
    public UserResponse getCustomerByPhone(String phone) {

        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toUserResponse(user);
    }

    @Override
    public User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        String username = authentication.getName();

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}

