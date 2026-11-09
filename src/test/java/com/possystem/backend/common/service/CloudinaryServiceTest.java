package com.possystem.backend.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.response.CloudinaryUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @InjectMocks
    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
    }

    @Test
    @DisplayName("Unit Test: Upload ảnh hợp lệ thành công (PNG < 5MB)")
    void uploadImage_Success() throws IOException {
        byte[] content = "valid image content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "product-test.png", "image/png", content
        );

        when(uploader.upload(eq(content), any(Map.class))).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/sample.png",
                "public_id", "products/sample_123"
        ));

        CloudinaryUploadResult result = cloudinaryService.uploadImage(file);

        assertNotNull(result);
        assertEquals("https://res.cloudinary.com/demo/image/upload/sample.png", result.getUrl());
        assertEquals("products/sample_123", result.getPublicId());
        verify(uploader, times(1)).upload(eq(content), any(Map.class));
    }

    @Test
    @DisplayName("Unit Test: Từ chối upload file rỗng")
    void uploadImage_EmptyFile_ThrowsException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]
        );

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadImage(emptyFile)
        );

        assertEquals(ErrorCode.UPLOAD_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("không được để trống"));
    }

    @Test
    @DisplayName("Unit Test: Từ chối upload file vượt quá giới hạn 5MB")
    void uploadImage_OversizedFile_ThrowsException() {
        byte[] largeContent = new byte[6 * 1024 * 1024]; // 6MB
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large-photo.jpg", "image/jpeg", largeContent
        );

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadImage(largeFile)
        );

        assertEquals(ErrorCode.UPLOAD_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("vượt quá giới hạn 5MB"));
    }

    @Test
    @DisplayName("Unit Test: Từ chối MIME type không an toàn (application/pdf)")
    void uploadImage_InvalidMimeType_ThrowsException() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "dummy pdf".getBytes()
        );

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadImage(pdfFile)
        );

        assertEquals(ErrorCode.UPLOAD_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Chỉ chấp nhận các định dạng ảnh"));
    }

    @Test
    @DisplayName("Unit Test: Từ chối extension độc hại dù giả mạo MIME type (shell.php)")
    void uploadImage_InvalidExtension_ThrowsException() {
        MockMultipartFile phpFile = new MockMultipartFile(
                "file", "shell.php", "image/png", "malicious payload".getBytes()
        );

        AppException ex = assertThrows(AppException.class, () ->
                cloudinaryService.uploadImage(phpFile)
        );

        assertEquals(ErrorCode.UPLOAD_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Phần mở rộng tệp không hợp lệ"));
    }

    @Test
    @DisplayName("Unit Test: Xóa ảnh thành công trên Cloudinary")
    void deleteImage_Success() throws IOException {
        when(uploader.destroy(eq("products/sample_123"), any(Map.class))).thenReturn(Map.of("result", "ok"));

        assertDoesNotThrow(() -> cloudinaryService.deleteImage("products/sample_123"));
        verify(uploader, times(1)).destroy(eq("products/sample_123"), any(Map.class));
    }
}

