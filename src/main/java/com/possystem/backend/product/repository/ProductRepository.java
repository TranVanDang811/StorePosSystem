package com.possystem.backend.product.repository;

import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product,String> {
    Optional<Product> findByProductCode(String productCode);
    long countByStatus(ProductStatus status);

    @Query("""
    SELECT p FROM Product p
    WHERE (:keyword IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:productCode IS NULL OR LOWER(p.productCode) LIKE LOWER(CONCAT('%', :productCode, '%')))
      AND (:categoryName IS NULL OR p.category.name = :categoryName)
      AND (:price IS NULL OR p.price = :price)
      AND (:status IS NULL OR p.status = :status)
      
""")
    Page<Product> filterProducts(@Param("keyword") String keyword,
                                 @Param("productCode") String productCode,
                                 @Param("categoryName") String categoryName,
                                 @Param("price") String price,
                                 @Param("status") ProductStatus status,
                                 Pageable pageable);


}