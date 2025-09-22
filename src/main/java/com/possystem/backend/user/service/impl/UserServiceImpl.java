package com.possystem.backend.user.service.impl;

import com.possystem.backend.common.constant.PredefinedRole;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.UserMapper;
import com.possystem.backend.role.entity.Role;
import com.possystem.backend.role.repository.RoleRepository;
import com.possystem.backend.user.dto.UserCreationRequest;
import com.possystem.backend.user.dto.UserResponse;
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
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
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
    @Transactional
    @PreAuthorize("hasRole('ADMIN') or hasRole('EMPLOYEE')")
    public UserResponse createUser(UserCreationRequest request) {

        User user = userMapper.toUser(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        Role role = roleRepository.findByName(request.getRoles())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        user.setRoles(Set.of(role));


        if (role.getName().equals("CUSTOMER")) {
            // Khách hàng thì không bắt buộc username, password, email
            user.setUsername(null);
            user.setPassword(null);
            user.setEmail(null);

            if (user.getCustomerProfile() != null) {
                user.getCustomerProfile().setUser(user);
            }
        } else {
            // Nhân viên / admin thì vẫn yêu cầu username, password, email
            if (request.getPassword() == null) {
                throw new AppException(ErrorCode.PASSWORD_REQUIRED);
            }
            user.setPassword(passwordEncoder.encode(request.getPassword()));

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

    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserResponse> getUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userRepository.findAll(pageable)
                .map(userMapper::toUserResponse);
    }

    @PostAuthorize("hasRole('ADMIN') or returnObject.username == authentication.name")
    public UserResponse getMyInfo() {
        var context = SecurityContextHolder.getContext();
        String name = context.getAuthentication().getName();

        User user = userRepository.findByUsername(name).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        return userMapper.toUserResponse(user);
    }
}

