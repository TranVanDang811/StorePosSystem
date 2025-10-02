package com.possystem.backend.supplier.repository;

import com.possystem.backend.supplier.entity.Supplier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier,String> {
    boolean existsByNameIgnoreCase(String name);
    Page<Supplier> findByNameContainingIgnoreCase(String keyword, Pageable pageable);

}
