package com.possystem.backend.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.possystem.backend.common.exception.GlobalExceptionHandler;
import com.possystem.backend.user.dto.UserCreationRequest;
import com.possystem.backend.user.dto.UserResponse;
import com.possystem.backend.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser(username = "staffUser", roles = {"EMPLOYEE"})
    @DisplayName("REST API Test: Lấy thông tin cá nhân getMyInfo thành công")
    void getMyInfo_Success() throws Exception {
        UserResponse response = UserResponse.builder()
                .username("staffUser")
                .fullName("Staff Name")
                .email("staff@pos.com")
                .build();

        when(userService.getMyInfo()).thenReturn(response);

        mockMvc.perform(get("/users/myInfo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.username").value("staffUser"))
                .andExpect(jsonPath("$.result.fullName").value("Staff Name"));
    }

    @Test
    @DisplayName("REST API Test: Validation - Từ chối tạo người dùng với password ngắn hơn 6 ký tự")
    void createUser_ShortPassword_Returns400() throws Exception {
        UserCreationRequest invalidRequest = UserCreationRequest.builder()
                .username("validuser")
                .password("123") // Quá ngắn (< 6 ký tự)
                .build();

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REST API Test: Validation - Từ chối tạo người dùng để trống username")
    void createUser_EmptyUsername_Returns400() throws Exception {
        UserCreationRequest invalidRequest = UserCreationRequest.builder()
                .username("")
                .password("validPassword123")
                .build();

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("REST API Test: Tạo người dùng hợp lệ trả về HTTP 200")
    void createUser_Success() throws Exception {
        UserCreationRequest request = UserCreationRequest.builder()
                .username("newcashier")
                .password("secretPass123")
                .fullName("New Cashier")
                .roles("EMPLOYEE")
                .build();

        UserResponse response = UserResponse.builder()
                .username("newcashier")
                .fullName("New Cashier")
                .build();

        when(userService.createUser(any(UserCreationRequest.class))).thenReturn(response);

        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.username").value("newcashier"));
    }
}

