package com.possystem.backend.role.service;

import com.possystem.backend.role.dto.RoleRequest;
import com.possystem.backend.role.dto.RoleResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RoleService {
    RoleResponse create(RoleRequest request);
    Page<RoleResponse> getAll(int page, int size);
    void delete(String role);
}
