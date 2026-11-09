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
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${app.admin.username:admin}")
    String adminUsername;

    @NonFinal
    @Value("${app.admin.password:admin}")
    String adminPassword;

    @Bean
    @ConditionalOnProperty(
            prefix = "spring",
            value = "datasource.driverClassName",
            havingValue = "com.mysql.cj.jdbc.Driver")
    ApplicationRunner applicationRunner(UserRepository userRepository,
                                        RoleRepository roleRepository,
                                        org.springframework.transaction.PlatformTransactionManager transactionManager) {
        log.info("Initializing application.....");
        var txTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        return args -> txTemplate.executeWithoutResult(txStatus -> {

            Role employeeRole = roleRepository.findByName(PredefinedRole.EMPLOYEE_ROLE)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name(PredefinedRole.EMPLOYEE_ROLE)
                            .description("Employee role")
                            .build()));

            Role manageRole = roleRepository.findByName(PredefinedRole.MANAGE_ROLE)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name(PredefinedRole.MANAGE_ROLE)
                            .description("Manager role")
                            .build()));
            Role customerRole = roleRepository.findByName(PredefinedRole.CUSTOMER_ROLE)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name(PredefinedRole.CUSTOMER_ROLE)
                            .description("Customer role")
                            .build()));


            Role adminRole = roleRepository.findByName(PredefinedRole.ADMIN_ROLE)
                    .orElseGet(() -> roleRepository.save(Role.builder()
                            .name(PredefinedRole.ADMIN_ROLE)
                            .description("Admin role")
                            .build()));


            var adminOpt = userRepository.findByUsername(adminUsername);
            if (adminOpt.isEmpty()) {
                User user = User.builder()
                        .username(adminUsername)
                        .password(passwordEncoder.encode(adminPassword))
                        .roles(Set.of(adminRole))
                        .build();
                userRepository.save(user);
                log.info("Admin user '{}' has been created.", adminUsername);
            } else {
                User existingAdmin = adminOpt.get();
                if (existingAdmin.getRoles() == null || !existingAdmin.getRoles().contains(adminRole)) {
                    var roles = existingAdmin.getRoles() == null ? new java.util.HashSet<Role>() : new java.util.HashSet<>(existingAdmin.getRoles());
                    roles.add(adminRole);
                    existingAdmin.setRoles(roles);
                    userRepository.save(existingAdmin);
                    log.info("Admin user '{}' updated with ADMIN role.", adminUsername);
                } else {
                    log.info("Admin user '{}' already exists. Skipping initialization.", adminUsername);
                }
            }

            log.info("Application initialization completed .....");
        });
    }
}
