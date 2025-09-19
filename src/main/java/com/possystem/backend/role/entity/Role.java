package com.possystem.backend.role.entity;

import java.util.Set;

import com.possystem.backend.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class Role{
    @Id
    String name;

    @Column(name = "description")
    String description;

    @ManyToMany(mappedBy = "roles")
    Set<User> users;
}