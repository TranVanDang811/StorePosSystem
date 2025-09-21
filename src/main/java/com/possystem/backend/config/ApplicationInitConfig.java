package com.possystem.backend.config;

import com.possystem.backend.common.constant.PredefinedRole;
import com.possystem.backend.role.entity.Role;
import com.possystem.backend.role.repository.RoleRepository;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ApplicationInitConfig {

    PasswordEncoder passwordEncoder;

    @NonFinal
    static final String ADMIN_USER_NAME = "admin";

    @NonFinal
    static final String ADMIN_PASSWORD = "admin";

    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.driverClassName",
            havingValue = "com.mysql.cj.jdbc.Driver")
    ApplicationRunner applicationRunner(UserRepository userRepository,
                                        RoleRepository roleRepository) {
        log.info("Initializing application.....");
        return args -> {
            // 1. Tạo role EMPLOYEE nếu chưa có
            Role employeeRole = roleRepository.findByName(PredefinedRole.EMPLOYEE_ROLE)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name(PredefinedRole.EMPLOYEE_ROLE)
                            .description("Employee role")
                            .build()));

            Role customerRole = roleRepository.findByName(PredefinedRole.CUSTOMER_ROLE)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name(PredefinedRole.CUSTOMER_ROLE)
                            .description("Customer role")
                            .build()));

            // 2. Tạo role ADMIN nếu chưa có
            Role adminRole = roleRepository.findByName(PredefinedRole.ADMIN_ROLE)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name(PredefinedRole.ADMIN_ROLE)
                            .description("Admin role")
                            .build()));

            // 3. Tạo admin user mặc định nếu chưa có
            if (userRepository.findByUsername(ADMIN_USER_NAME).isEmpty()) {
                User user = User.builder()
                        .username(ADMIN_USER_NAME)
                        .password(passwordEncoder.encode(ADMIN_PASSWORD))
                        .roles(Set.of(adminRole, employeeRole,customerRole))
                        .build();
                userRepository.save(user);
                log.warn("Admin user has been created with default password: admin, please change it");
            }

            log.info("Application initialization completed .....");
        };
    }
}
