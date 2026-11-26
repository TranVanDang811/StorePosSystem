package com.possystem.backend.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.possystem.backend.common.exception.AppException;
import com.possystem.backend.common.exception.ErrorCode;
import com.possystem.backend.common.observability.PosBusinessMetrics;
import com.possystem.backend.common.observability.PosTracingHelper;
import com.possystem.backend.common.response.CloudinaryUploadResult;
import io.micrometer.tracing.Span;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CloudinaryService {
    private final Cloudinary cloudinary;
    private final PosBusinessMetrics businessMetrics;
    private final PosTracingHelper posTracingHelper;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/jpg");

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp");

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    public CloudinaryUploadResult uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.UPLOAD_FAILED, "Tệp tải lên không được để trống");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new AppException(ErrorCode.UPLOAD_FAILED, "Dung lượng tệp vượt quá giới hạn 5MB");
        }

        // Validate MIME type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new AppException(ErrorCode.UPLOAD_FAILED, "Chỉ chấp nhận các định dạng ảnh: JPG, PNG, WEBP");
        }

        // Validate File Extension
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lowerFilename = originalFilename.toLowerCase();
            boolean hasValidExt = ALLOWED_EXTENSIONS.stream().anyMatch(lowerFilename::endsWith);
            if (!hasValidExt) {
                throw new AppException(ErrorCode.UPLOAD_FAILED, "Phần mở rộng tệp không hợp lệ");
            }
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new AppException(ErrorCode.UPLOAD_FAILED, "Không thể đọc dữ liệu tệp");
        }

        // Validate Magic Bytes chống giả mạo MIME type
        if (!isValidImageMagicBytes(fileBytes)) {
            throw new AppException(ErrorCode.UPLOAD_FAILED,
                    "Nội dung tệp không phải là ảnh hợp lệ (Magic Bytes mismatch)");
        }

        long startNanos = System.nanoTime();
        String outcome = "FAILED";
        try {
            PosTracingHelper.CheckedSupplier<Map, IOException> uploadOp = () -> cloudinary.uploader().upload(
                    fileBytes, ObjectUtils.asMap("resource_type", "image"));
            Map uploadResult = posTracingHelper != null
                    ? posTracingHelper.traceChecked(
                            "pos.external.cloudinary.upload",
                            Span.Kind.CLIENT,
                            null,
                            Map.of(
                                    "external.provider", "cloudinary",
                                    "external.operation", "upload",
                                    "file.size_bytes", String.valueOf(fileBytes.length)),
                            uploadOp)
                    : uploadOp.get();
            String url = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            outcome = "SUCCESS";
            return new CloudinaryUploadResult(url, publicId);
        } catch (IOException e) {
            throw new AppException(ErrorCode.UPLOAD_FAILED);
        } finally {
            if (businessMetrics != null) {
                businessMetrics.recordExternalCall("cloudinary", "upload", outcome, System.nanoTime() - startNanos);
            }
        }
    }

    private boolean isValidImageMagicBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 12)
            return false;
        // JPEG: FF D8 FF
        boolean isJpeg = (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8 && bytes[2] == (byte) 0xFF);
        // PNG: 89 50 4E 47
        boolean isPng = (bytes[0] == (byte) 0x89 && bytes[1] == (byte) 0x50 && bytes[2] == (byte) 0x4E
                && bytes[3] == (byte) 0x47);
        // WEBP: 'RIFF' .... 'WEBP'
        boolean isWebp = (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P');
        return isJpeg || isPng || isWebp;
    }

    public void deleteImage(String publicId) {
        long startNanos = System.nanoTime();
        String outcome = "FAILED";
        try {
            PosTracingHelper.CheckedSupplier<Map, IOException> destroyOp = () -> cloudinary.uploader().destroy(
                    publicId, ObjectUtils.emptyMap());
            if (posTracingHelper != null) {
                posTracingHelper.traceChecked(
                        "pos.external.cloudinary.delete",
                        Span.Kind.CLIENT,
                        null,
                        Map.of("external.provider", "cloudinary", "external.operation", "delete"),
                        destroyOp);
            } else {
                destroyOp.get();
            }
            outcome = "SUCCESS";
        } catch (IOException e) {
            throw new AppException(ErrorCode.SERVER_ERROR);
        } finally {
            if (businessMetrics != null) {
                businessMetrics.recordExternalCall("cloudinary", "delete", outcome, System.nanoTime() - startNanos);
            }
        }
    }
}
