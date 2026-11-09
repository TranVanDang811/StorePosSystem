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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private UserCreationRequest creationRequest;
    private Role employeeRole;
    private User savedUser;
    private UserResponse expectedResponse;

    @BeforeEach
    void setUp() {
        creationRequest = UserCreationRequest.builder()
                .username("employee01")
                .password("plainSecret123")
                .fullName("Tran Van B")
                .email("b@example.com")
                .roles("EMPLOYEE")
                .build();

        employeeRole = Role.builder().name(PredefinedRole.EMPLOYEE_ROLE).build();

        savedUser = User.builder()
                .username("employee01")
                .password("encodedPassword")
                .fullName("Tran Van B")
                .email("b@example.com")
                .roles(Set.of(employeeRole))
                .build();

        expectedResponse = UserResponse.builder()
                .username("employee01")
                .fullName("Tran Van B")
                .email("b@example.com")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(String roleName) {
        Authentication auth = mock(Authentication.class);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_" + roleName))).when(auth).getAuthorities();
        doReturn("adminUser").when(auth).getName();

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("Unit Test: Tạo người dùng mới thành công với phân quyền hợp lệ")
    void createUser_Success() {
        mockSecurityContext("ADMIN");

        when(userMapper.toUser(creationRequest)).thenReturn(new User());
        when(passwordEncoder.encode("plainSecret123")).thenReturn("encodedPassword");
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toUserResponse(any(User.class))).thenReturn(expectedResponse);

        UserResponse actualResponse = userService.createUser(creationRequest);

        assertNotNull(actualResponse);
        assertEquals("employee01", actualResponse.getUsername());
        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder, times(1)).encode("plainSecret123");
    }

    @Test
    @DisplayName("Unit Test: Ngăn tạo tài khoản trùng username (DataIntegrityViolationException)")
    void createUser_DuplicateUsername_ThrowsException() {
        mockSecurityContext("ADMIN");

        when(userMapper.toUser(creationRequest)).thenReturn(new User());
        when(passwordEncoder.encode("plainSecret123")).thenReturn("encodedPassword");
        when(roleRepository.findByName("EMPLOYEE")).thenReturn(Optional.of(employeeRole));
        when(userRepository.save(any(User.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry"));

        AppException ex = assertThrows(AppException.class, () ->
                userService.createUser(creationRequest)
        );

        assertEquals(ErrorCode.USERNAME_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("Unit Test: Chống leo thang đặc quyền (Privilege Escalation) - Nhân viên không thể gán quyền ADMIN")
    void createUser_PrivilegeEscalation_ThrowsException() {
        // Giả lập caller chỉ có quyền EMPLOYEE
        mockSecurityContext("EMPLOYEE");

        // Yêu cầu tạo user với role ADMIN
        UserCreationRequest adminRequest = UserCreationRequest.builder()
                .username("hacker")
                .password("password123")
                .roles("ADMIN")
                .build();

        AppException ex = assertThrows(AppException.class, () ->
                userService.createUser(adminRequest)
        );

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Unit Test: Lấy thông tin cá nhân (getMyInfo) của user đang đăng nhập")
    void getMyInfo_Success() {
        mockSecurityContext("EMPLOYEE");

        when(userRepository.findByUsername("adminUser")).thenReturn(Optional.of(savedUser));
        when(userMapper.toUserResponse(savedUser)).thenReturn(expectedResponse);

        UserResponse response = userService.getMyInfo();

        assertNotNull(response);
        assertEquals("employee01", response.getUsername());
        verify(userRepository, times(1)).findByUsername("adminUser");
    }
}
