package com.possystem.backend.supplier.repository;

import com.possystem.backend.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, String> {
    boolean existsByNameIgnoreCase(String name);
    Page<Supplier> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
    Optional<Supplier> findByName(String name);

    @Query("""
        SELECT s FROM Supplier s
        WHERE (:keyword IS NULL OR :keyword = ''
               OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(s.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(s.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(s.address) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    Page<Supplier> filterSuppliers(@Param("keyword") String keyword, Pageable pageable);
}
