package com.possystem.backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class CustomerPointHistory extends AbstractEntity{


    Integer pointsChanged;   // +1, +2, -3 ...
    String reason;           // "Earn from Order #123", "Redeem in Order #456"

    @ManyToOne
    Order order;
}
