package com.possystem.backend.shift.repository;


import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.shift.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, String> {

    Optional<Shift> findByStatus(ShiftStatus status);
    Optional<Shift> findTopByStatusOrderByEndTimeDesc(ShiftStatus status);
    Optional<Shift> findByShiftCode(String shiftCode);
}
