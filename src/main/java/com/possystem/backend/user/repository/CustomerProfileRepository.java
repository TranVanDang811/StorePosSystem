package com.possystem.backend.user.repository;

import com.possystem.backend.user.entity.CustomerProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cp FROM CustomerProfile cp WHERE cp.id = :id")
    Optional<CustomerProfile> findByIdWithLock(@Param("id") String id);
}
