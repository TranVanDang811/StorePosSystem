package com.possystem.backend.shift.service.impl;

import com.possystem.backend.common.enums.ShiftStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.ShiftMapper;
import com.possystem.backend.payment.repository.PaymentRepository;
import com.possystem.backend.shift.dto.CloseShiftRequest;
import com.possystem.backend.shift.dto.OpenShiftRequest;
import com.possystem.backend.shift.dto.ShiftResponse;
import com.possystem.backend.shift.entity.Shift;
import com.possystem.backend.shift.repository.ShiftRepository;
import com.possystem.backend.user.entity.User;
import com.possystem.backend.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private ShiftMapper shiftMapper;

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ShiftServiceImpl shiftService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username("cashier01")
                .fullName("Nguyen Van A")
                .build();
        testUser.setId("user-1");
    }

    @Test
    @DisplayName("Unit Test: Mở ca làm việc thành công khi chưa có ca nào mở")
    void openShift_Success() {
        OpenShiftRequest request = OpenShiftRequest.builder()
                .openingCash(BigDecimal.valueOf(1000000))
                .note("Đầu ca sáng")
                .build();

        when(userService.getCurrentUser()).thenReturn(testUser);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Optional.empty());

        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> {
            Shift shift = invocation.getArgument(0);
            shift.setId("shift-123");
            return shift;
        });

        when(shiftMapper.toResponse(any(Shift.class))).thenAnswer(invocation -> {
            Shift shift = invocation.getArgument(0);
            return ShiftResponse.builder()
                    .shiftCode(shift.getShiftCode())
                    .status(shift.getStatus())
                    .openingCash(shift.getOpeningCash())
                    .build();
        });

        ShiftResponse response = shiftService.openShift(request);

        assertNotNull(response);
        assertEquals(ShiftStatus.OPEN, response.getStatus());
        assertEquals(BigDecimal.valueOf(1000000), response.getOpeningCash());
        verify(shiftRepository, times(1)).save(any(Shift.class));
    }

    @Test
    @DisplayName("Unit Test: Ném ngoại lệ khi mở ca nếu đã có ca đang OPEN")
    void openShift_AlreadyOpened_ThrowsException() {
        OpenShiftRequest request = OpenShiftRequest.builder()
                .openingCash(BigDecimal.valueOf(500000))
                .build();

        when(userService.getCurrentUser()).thenReturn(testUser);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Optional.of(Shift.builder().status(ShiftStatus.OPEN).build()));

        AppException ex = assertThrows(AppException.class, () -> shiftService.openShift(request));
        assertEquals(ErrorCode.SHIFT_ALREADY_OPENED, ex.getErrorCode());
        verify(shiftRepository, never()).save(any(Shift.class));
    }

    @Test
    @DisplayName("Unit Test: Đóng ca thành công và tính chính xác chênh lệch tiền mặt")
    void closeShift_Success() {
        Shift openShift = Shift.builder()
                .shiftCode("SHIFT-20261123-001")
                .status(ShiftStatus.OPEN)
                .openingCash(BigDecimal.valueOf(1000000))
                .startTime(LocalDateTime.now().minusHours(8))
                .openedBy(testUser)
                .build();
        openShift.setId("shift-1");

        CloseShiftRequest request = CloseShiftRequest.builder()
                .note500k(4L) // 4 * 500k = 2,000,000
                .note200k(5L) // 5 * 200k = 1,000,000
                .build();
        // Tổng tiền kiểm đếm: 3,000,000 VND

        when(userService.getCurrentUser()).thenReturn(testUser);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Optional.of(openShift));

        // Tiền bán cash: 2,500,000, hoàn tiền cash: 500,000
        // Expected = 1,000,000 (đầu ca) + 2,500,000 (bán) - 500,000 (hoàn) = 3,000,000
        when(paymentRepository.sumCashByShift(any(), any())).thenReturn(BigDecimal.valueOf(2500000));
        when(paymentRepository.sumCashRefunds(any(), any())).thenReturn(BigDecimal.valueOf(500000));
        when(paymentRepository.countPaidOrders(any(), any())).thenReturn(15L);
        when(paymentRepository.countCustomers(any(), any())).thenReturn(12L);

        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(shiftMapper.toResponse(any(Shift.class))).thenAnswer(invocation -> {
            Shift s = invocation.getArgument(0);
            return ShiftResponse.builder()
                    .shiftCode(s.getShiftCode())
                    .status(s.getStatus())
                    .difference(s.getDifference())
                    .expectedCash(s.getExpectedCash())
                    .countedCash(s.getCountedCash())
                    .build();
        });

        ShiftResponse response = shiftService.closeShift(request);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(3000000), response.getCountedCash());
        assertEquals(BigDecimal.valueOf(3000000), response.getExpectedCash());
        assertEquals(BigDecimal.ZERO, response.getDifference()); // Không lệch tiền
        verify(shiftRepository, times(1)).save(any(Shift.class));
    }

    @Test
    @DisplayName("Unit Test: Ném ngoại lệ khi đóng ca nếu không có ca nào đang OPEN")
    void closeShift_NoOpenShift_ThrowsException() {
        CloseShiftRequest request = CloseShiftRequest.builder().build();

        when(userService.getCurrentUser()).thenReturn(testUser);
        when(shiftRepository.findByStatus(ShiftStatus.OPEN)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> shiftService.closeShift(request));
        assertEquals(ErrorCode.NO_OPENED_SHIFT, ex.getErrorCode());
        verify(shiftRepository, never()).save(any(Shift.class));
    }
}
