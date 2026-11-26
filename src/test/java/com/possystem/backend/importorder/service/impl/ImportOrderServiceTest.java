package com.possystem.backend.importorder.service.impl;

import com.possystem.backend.common.enums.ConfirmStatus;
import com.possystem.backend.common.enums.ImportStatus;
import com.possystem.backend.common.enums.ProductStatus;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.util.mapper.ImportOrderMapper;
import com.possystem.backend.importorder.dto.ImportOrderCreateRequest;
import com.possystem.backend.importorder.dto.ImportOrderDetailRequest;
import com.possystem.backend.importorder.dto.ImportOrderResponse;
import com.possystem.backend.importorder.entity.ImportOrder;
import com.possystem.backend.importorder.entity.ImportOrderDetail;
import com.possystem.backend.importorder.repository.ImportOrderDetailRepository;
import com.possystem.backend.importorder.repository.ImportOrderRepository;
import com.possystem.backend.product.entity.Product;
import com.possystem.backend.product.repository.ProductRepository;
import com.possystem.backend.supplier.entity.Supplier;
import com.possystem.backend.supplier.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportOrderServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ImportOrderRepository importOrderRepository;

    @Mock
    private ImportOrderMapper importOrderMapper;

    @Mock
    private ImportOrderDetailRepository importOrderDetailRepository;

    @InjectMocks
    private ImportOrderServiceImpl importOrderService;

    private Supplier testSupplier;
    private Product testProduct;

    @BeforeEach
    void setUp() {
        testSupplier = Supplier.builder()
                .name("Nhà Cung Cấp Vinamilk")
                .build();
        testSupplier.setId("sup-1");

        testProduct = Product.builder()
                .productCode("PROD-01")
                .name("Sữa tươi tiệt trùng 1L")
                .stock(10)
                .cost(BigDecimal.valueOf(50000))
                .status(ProductStatus.ACTIVE)
                .supplier(testSupplier)
                .build();
        testProduct.setId("prod-1");
    }

    @Test
    @DisplayName("Unit Test: Tạo đơn nhập hàng thành công ở trạng thái DRAFT")
    void createImportOrder_Success() {
        ImportOrderDetailRequest detailRequest = ImportOrderDetailRequest.builder()
                .productId("prod-1")
                .quantity(10)
                .importPrice(BigDecimal.valueOf(70000))
                .build();

        ImportOrderCreateRequest request = ImportOrderCreateRequest.builder()
                .supplierId("sup-1")
                .note("Nhập hàng đợt 1")
                .importDetails(List.of(detailRequest))
                .build();

        ImportOrder savedMock = ImportOrder.builder()
                .id("io-1")
                .supplier(testSupplier)
                .status(ImportStatus.DRAFT)
                .confirmStatus(ConfirmStatus.UNCONFIRMED)
                .totalQuantity(10)
                .totalPrice(BigDecimal.valueOf(700000))
                .build();

        when(supplierRepository.findById("sup-1")).thenReturn(Optional.of(testSupplier));
        when(importOrderMapper.toEntity(any(ImportOrderCreateRequest.class))).thenReturn(new ImportOrder());
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(testProduct));
        when(importOrderRepository.save(any(ImportOrder.class))).thenReturn(savedMock);
        when(importOrderRepository.findByIdWithDetails("io-1")).thenReturn(Optional.of(savedMock));
        when(importOrderMapper.toResponse(savedMock)).thenReturn(
                ImportOrderResponse.builder()
                        .id("io-1")
                        .status(ImportStatus.DRAFT)
                        .totalQuantity(10)
                        .totalPrice(BigDecimal.valueOf(700000))
                        .build()
        );

        ImportOrderResponse response = importOrderService.createImportOrder(request);

        assertNotNull(response);
        assertEquals("io-1", response.getId());
        assertEquals(ImportStatus.DRAFT, response.getStatus());
        assertEquals(10, response.getTotalQuantity());
        verify(importOrderRepository, times(1)).save(any(ImportOrder.class));
    }

    @Test
    @DisplayName("Unit Test: Ném ngoại lệ khi tạo đơn nhập hàng không có sản phẩm")
    void createImportOrder_EmptyDetails_ThrowsException() {
        ImportOrderCreateRequest request = ImportOrderCreateRequest.builder()
                .supplierId("sup-1")
                .importDetails(new ArrayList<>())
                .build();

        assertThrows(IllegalArgumentException.class, () -> importOrderService.createImportOrder(request));
    }

    @Test
    @DisplayName("Unit Test: Ném ngoại lệ khi sản phẩm không thuộc nhà cung cấp của đơn nhập")
    void createImportOrder_ProductNotBelongToSupplier_ThrowsException() {
        Supplier anotherSupplier = Supplier.builder().name("Khác").build();
        anotherSupplier.setId("sup-999");
        testProduct.setSupplier(anotherSupplier);

        ImportOrderDetailRequest detailRequest = ImportOrderDetailRequest.builder()
                .productId("prod-1")
                .quantity(5)
                .importPrice(BigDecimal.valueOf(50000))
                .build();

        ImportOrderCreateRequest request = ImportOrderCreateRequest.builder()
                .supplierId("sup-1")
                .importDetails(List.of(detailRequest))
                .build();

        when(supplierRepository.findById("sup-1")).thenReturn(Optional.of(testSupplier));
        when(importOrderMapper.toEntity(any(ImportOrderCreateRequest.class))).thenReturn(new ImportOrder());
        when(productRepository.findById("prod-1")).thenReturn(Optional.of(testProduct));

        AppException ex = assertThrows(AppException.class, () -> importOrderService.createImportOrder(request));
        assertEquals(ErrorCode.PRODUCT_NOT_BELONG_TO_SUPPLIER, ex.getErrorCode());
        verify(importOrderRepository, never()).save(any(ImportOrder.class));
    }

    @Test
    @DisplayName("Unit Test: Chốt nhập kho (confirmAndFinalize) - Tính chuẩn giá vốn bình quân và cộng dồn tồn kho")
    void confirmAndFinalize_Success_UpdatesStockAndWeightedAverageCost() {
        // Tồn kho cũ = 10, giá vốn cũ = 50,000
        // Nhập mới = 10, giá nhập = 70,000
        // Tồn mới = 20, Giá vốn bình quân mới = ((10 * 50,000) + (10 * 70,000)) / 20 = 60,000.00
        ImportOrderDetail detail = ImportOrderDetail.builder()
                .product(testProduct)
                .quantity(10)
                .receivedQuantity(10)
                .confirmedQuantity(0)
                .importPrice(BigDecimal.valueOf(70000))
                .build();

        List<ImportOrderDetail> details = new ArrayList<>();
        details.add(detail);

        ImportOrder order = ImportOrder.builder()
                .id("io-1")
                .supplier(testSupplier)
                .status(ImportStatus.DRAFT)
                .totalQuantity(10)
                .importDate(LocalDateTime.now())
                .importDetails(details)
                .build();

        when(importOrderRepository.findByIdWithDetails("io-1")).thenReturn(Optional.of(order));
        when(importOrderRepository.save(any(ImportOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(importOrderMapper.toResponse(any(ImportOrder.class))).thenReturn(
                ImportOrderResponse.builder().id("io-1").status(ImportStatus.IMPORTED).build()
        );

        ImportOrderResponse response = importOrderService.confirmAndFinalize("io-1");

        assertNotNull(response);
        assertEquals(20, testProduct.getStock());
        assertEquals(BigDecimal.valueOf(60000).setScale(2, RoundingMode.HALF_UP), testProduct.getCost());
        assertEquals(10, detail.getConfirmedQuantity());
        assertEquals(ImportStatus.IMPORTED, order.getStatus());
        assertEquals(ConfirmStatus.CONFIRMED, order.getConfirmStatus());

        verify(productRepository, times(1)).save(testProduct);
        verify(importOrderRepository, times(1)).save(order);
    }

    @Test
    @DisplayName("Unit Test: Ném ngoại lệ khi hủy đơn hàng đã hoàn tất nhập kho")
    void cancelImportOrder_AlreadyImported_ThrowsIllegalStateException() {
        ImportOrder order = ImportOrder.builder()
                .id("io-1")
                .status(ImportStatus.IMPORTED)
                .build();

        when(importOrderRepository.findByIdWithDetails("io-1")).thenReturn(Optional.of(order));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> importOrderService.cancelImportOrder("io-1"));

        assertTrue(ex.getMessage().contains("Không thể hủy đơn hàng đã hoàn thành nhập kho"));
    }
}
