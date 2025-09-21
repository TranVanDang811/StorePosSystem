package com.possystem.backend.user.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.possystem.backend.entity.AbstractEntity;

import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.role.entity.Role;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class User extends AbstractEntity {

    @Column(name = "username", unique = true, columnDefinition = "VARCHAR(255) COLLATE utf8mb4_unicode_ci")
    String username;

    String password;
    String fullName;
    String email;
    String phone;

    @ManyToMany
    @JoinTable(
            name = "user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    Set<Role> roles;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    UserStatus status;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonManagedReference
    CustomerProfile customerProfile;

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    @JsonManagedReference
     EmployeeProfile employeeProfile;

}
