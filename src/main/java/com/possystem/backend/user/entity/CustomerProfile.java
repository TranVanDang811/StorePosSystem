package com.possystem.backend.user.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CustomerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    int loyaltyPoints; // điểm tích lũy
    String membershipLevel; // hạng thành viên (Silver, Gold...)

    @OneToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    User user;

}
