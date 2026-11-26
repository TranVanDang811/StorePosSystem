package com.possystem.backend.shift.repository;


import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.shift.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, String> {

    Optional<Shift> findByStatus(ShiftStatus status);
    Optional<Shift> findByStatusAndTerminalId(ShiftStatus status, String terminalId);
    boolean existsByStatus(ShiftStatus status);
    boolean existsByStatusAndTerminalId(ShiftStatus status, String terminalId);
    Optional<Shift> findTopByStatusOrderByEndTimeDesc(ShiftStatus status);
    Optional<Shift> findTopByStatusAndTerminalIdOrderByEndTimeDesc(ShiftStatus status, String terminalId);
    Optional<Shift> findByShiftCode(String shiftCode);
}
