package com.possystem.backend.category.repository;

import com.possystem.backend.category.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    Optional<Category> findByName(String name);
    Page<Category> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
        SELECT c FROM Category c
        WHERE (:keyword IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    Page<Category> filterCategories(@Param("keyword") String keyword, Pageable pageable);
}
