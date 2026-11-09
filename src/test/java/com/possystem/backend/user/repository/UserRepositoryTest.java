package com.possystem.backend.user.repository;

import com.possystem.backend.role.entity.Role;
import com.possystem.backend.role.repository.RoleRepository;
import com.possystem.backend.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    @DisplayName("Repository Test: Lưu người dùng và tìm kiếm theo Username")
    void saveAndFindByUsername() {
        Role role = roleRepository.save(Role.builder().name("STAFF").description("Staff Role").build());

        User user = User.builder()
                .username("test_staff_01")
                .password("hash123")
                .fullName("Test Staff")
                .roles(Set.of(role))
                .build();

        userRepository.save(user);

        Optional<User> found = userRepository.findByUsername("test_staff_01");
        assertTrue(found.isPresent());
        assertEquals("test_staff_01", found.get().getUsername());
        assertEquals("Test Staff", found.get().getFullName());
    }

    @Test
    @DisplayName("Repository Test: Kiểm tra tồn tại username existsByUsername")
    void existsByUsername() {
        User user = User.builder()
                .username("cashier_unique")
                .password("hash123")
                .build();

        userRepository.save(user);

        assertTrue(userRepository.existsByUsername("cashier_unique"));
        assertFalse(userRepository.existsByUsername("non_existent_cashier"));
    }

    @Test
    @DisplayName("Repository Test: Ràng buộc Unique Constraint không cho phép 2 username trùng nhau")
    void uniqueConstraintOnUsername() {
        User user1 = User.builder()
                .username("duplicate_user")
                .password("hash123")
                .build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .username("duplicate_user")
                .password("hash456")
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(user2);
        });
    }
}
