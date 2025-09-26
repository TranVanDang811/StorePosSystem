package com.possystem.backend.user.repository;


import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Page<User> findByFullNameContainingIgnoreCase(String fullName, Pageable pageable);
    Page<User> findByFullNameContainingIgnoreCaseAndStatus(String keyword, UserStatus status, Pageable pageable);
    Page<User> findByFullNameContainingIgnoreCaseAndRoles_Name(String keyword, String roleName, Pageable pageable);
    Page<User> findByFullNameContainingIgnoreCaseAndRoles_NameAndStatus(String keyword, String roleName, UserStatus status, Pageable pageable);
    Page<User> findByRoles_Name(String roleName, Pageable pageable);
    Page<User> findByRoles_NameAndStatus(String roleName, UserStatus status, Pageable pageable);
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    Optional<User>findByEmail(String email);
}
