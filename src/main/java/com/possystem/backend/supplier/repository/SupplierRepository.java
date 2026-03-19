package com.possystem.backend.supplier.repository;

import com.possystem.backend.category.entity.Category;
import com.possystem.backend.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier,String> {
    boolean existsByNameIgnoreCase(String name);
    Page<Supplier> findByNameContainingIgnoreCase(String keyword, Pageable pageable);
    Optional<Supplier> findByName(String name);
}
