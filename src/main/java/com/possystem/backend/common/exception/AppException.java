package com.possystem.backend.common.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detailMessage;

    // Dùng khi chỉ cần ErrorCode
    public AppException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detailMessage = null;
    }

    // Dùng khi cần thêm chi tiết
    public AppException(ErrorCode errorCode, String detailMessage) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detailMessage = detailMessage;
    }

    // Dùng khi muốn wrap exception gốc
    public AppException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detailMessage = null;
    }

    // Dùng khi muốn format message động
    // Ví dụ: new AppException(ErrorCode.RESOURCE_NOT_FOUND, "User")
    public AppException(ErrorCode errorCode, Object... args) {
        super(String.format(errorCode.getMessage(), args));
        this.errorCode = errorCode;
        this.detailMessage = null;
    }

    @Override
    public String getMessage() {
        if (detailMessage != null) {
            return errorCode.getMessage() + ": " + detailMessage;
        }
        return super.getMessage();
    }
}
