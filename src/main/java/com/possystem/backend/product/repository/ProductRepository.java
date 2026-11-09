package com.possystem.backend.product.repository;

import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.product.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
  Optional<Product> findByProductCode(String productCode);

  Optional<Product> findByProductCodeIgnoreCase(String productCode);

  long countByStatus(ProductStatus status);

  boolean existsByProductCode(String productCode);

  boolean existsByProductCodeIgnoreCase(String productCode);

  List<Product> findBySupplier_Id(String supplierId);

  long countBySupplier_Id(String supplierId);

  long countByCategory_Id(String categoryId);

  @Query("SELECT p.category.id, COUNT(p) FROM Product p GROUP BY p.category.id")
  List<Object[]> countProductsGroupedByCategory();

  @Query("SELECT p.supplier.id, COUNT(p) FROM Product p WHERE p.supplier IS NOT NULL GROUP BY p.supplier.id")
  List<Object[]> countProductsGroupedBySupplier();

  long countBySupplierIsNotNull();

  @Query("""
          SELECT COUNT(p) FROM Product p
          WHERE (p.stock <= 0 OR p.status = com.possystem.backend.common.enums.ProductStatus.OUT_OF_STOCK)
            AND p.status != com.possystem.backend.common.enums.ProductStatus.DISCONTINUED
      """)
  long countOutOfStock();

  @Query("""
          SELECT COUNT(p) FROM Product p
          WHERE p.stock > 0 AND p.stock <= :threshold
            AND p.status = com.possystem.backend.common.enums.ProductStatus.ACTIVE
      """)
  long countLowStock(@Param("threshold") int threshold);

  @Query("""
          SELECT p FROM Product p
          WHERE (:keyword IS NULL OR :keyword = ''
                 OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR (p.productCode IS NOT NULL AND LOWER(p.productCode) LIKE LOWER(CONCAT('%', :keyword, '%'))))
            AND (:productCode IS NULL OR :productCode = ''
                 OR (p.productCode IS NOT NULL AND LOWER(p.productCode) LIKE LOWER(CONCAT('%', :productCode, '%'))))
            AND (:categoryName IS NULL OR :categoryName = '' OR p.category.name = :categoryName)
            AND (:price IS NULL OR p.price = :price)
            AND (:status IS NULL OR p.status = :status)
      """)
  Page<Product> filterProducts(@Param("keyword") String keyword,
      @Param("productCode") String productCode,
      @Param("categoryName") String categoryName,
      @Param("price") BigDecimal price,
      @Param("status") ProductStatus status,
      Pageable pageable);
}
