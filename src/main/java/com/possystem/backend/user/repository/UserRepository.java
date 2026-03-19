package com.possystem.backend.user.repository;


import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);
    Page<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String username,
            String fullName,
            Pageable pageable
    );

    Page<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCaseAndRoles_Name(
            String username,
            String fullName,
            String roleName,
            Pageable pageable
    );

    Page<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCaseAndStatus(
            String username,
            String fullName,
            UserStatus status,
            Pageable pageable
    );

    Page<User> findByUsernameContainingIgnoreCaseOrFullNameContainingIgnoreCaseAndRoles_NameAndStatus(
            String username,
            String fullName,
            String roleName,
            UserStatus status,
            Pageable pageable
    );
    Page<User> findByRoles_Name(String roleName, Pageable pageable);
    Page<User> findByRoles_NameAndStatus(String roleName, UserStatus status, Pageable pageable);
    Page<User> findByStatus(UserStatus status, Pageable pageable);
    Optional<User>findByEmail(String email);
    Optional<User> findByPhone(String phone);

    @Query("""
SELECT COUNT(u)
FROM User u
JOIN u.roles r
WHERE r.name = 'CUSTOMER'
AND u.createdAt BETWEEN :start AND :end
""")
    long countNewMembersToday(LocalDateTime start, LocalDateTime end);
}
