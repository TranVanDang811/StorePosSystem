package com.possystem.backend.role.controller;

import com.possystem.backend.common.response.ApiResponse;
import com.possystem.backend.role.dto.RoleRequest;
import com.possystem.backend.role.dto.RoleResponse;
import com.possystem.backend.role.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
@Tag(name = "Role Management", description = "User role management API")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoleController {
    RoleService roleService;
    @PostMapping
    @Operation(summary = "Create new role", description = "Add new role to the system")
    ApiResponse<RoleResponse> create(@RequestBody RoleRequest request) {
        return ApiResponse.<RoleResponse>builder()
                .result(roleService.create(request))
                .build();
    }

    @GetMapping
    @Operation(summary = "Get all roles", description = "Get a list of all roles")
    ApiResponse<Page<RoleResponse>> getAll(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "5") int size) {
        return ApiResponse.<Page<RoleResponse>>builder()
                .result(roleService.getAll(page - 1, size))
                .build();
    }


    @DeleteMapping("/{role}")
    @Operation(summary = "Delete role", description = "Delete role by name or id (depending on business rule)")
    ApiResponse<Void> delete(@PathVariable String role) {
        roleService.delete(role);
        return ApiResponse.<Void>builder().build();
    }
}
