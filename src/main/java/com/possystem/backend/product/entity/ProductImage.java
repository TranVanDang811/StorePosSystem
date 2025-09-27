package com.possystem.backend.product.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.util.Date;
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class ProductImage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String imageUrl;  // Đường dẫn ảnh

    String publicId;
    @Column(name = "created_at")
    @CreationTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    Date createdAt;

    @OneToOne(optional = true)
    @JoinColumn(name = "product_id", nullable = false)
    Product product;
}
