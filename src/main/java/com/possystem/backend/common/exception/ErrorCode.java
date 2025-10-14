package com.possystem.backend.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // ===== System errors =====
    UNCATEGORIZED_EXCEPTION(1000, "Uncategorized error", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_SERVER_ERROR(1001, "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    SERVER_ERROR(1002, "Error deleting photos on Cloudinary", HttpStatus.INTERNAL_SERVER_ERROR),
    // ===== Auth errors =====
    UNAUTHENTICATED(2001, "Unauthenticated", HttpStatus.UNAUTHORIZED),
    UNAUTHORIZED(2002, "You do not have permission", HttpStatus.FORBIDDEN),
    TOKEN_EXPIRED(2003, "Token expired", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(2004, "Invalid token", HttpStatus.BAD_REQUEST),
    TOKEN_GENERATION_FAILED(2005, "Token generation failed", HttpStatus.BAD_REQUEST),
    // ===== Validation errors =====
    INVALID_KEY(3000, "Invalid validation key", HttpStatus.BAD_REQUEST),
    FIRSTNAME_INVALID(3001, "FirstName must be at least {min} characters", HttpStatus.BAD_REQUEST),
    USERNAME_INVALID(3002, "Username must be at least {min} characters", HttpStatus.BAD_REQUEST),
    PASSWORD_INVALID(3003, "Password must be at least {min} characters", HttpStatus.BAD_REQUEST),
    INVALID_DOB(3004, "Your age must be at least {min}", HttpStatus.BAD_REQUEST),
    INVALID_QUANTITY(3005, "Quantity must be between {min} and {max}", HttpStatus.BAD_REQUEST),
    PASSWORD_REQUIRED(3006, "Password required", HttpStatus.BAD_REQUEST),
    INVALID_PASSWORD(3007,"Incorrect old password provided", HttpStatus.BAD_REQUEST),
    UPLOAD_FAILED(3008, "Image upload failed", HttpStatus.BAD_REQUEST),
    INVALID_JSON(3009, "Invalid JSON format", HttpStatus.BAD_REQUEST),
    COUPON_INVALID(3010,"The coupon code has expired or is not yet valid.",HttpStatus.BAD_REQUEST),
    INVALID_DISCOUNT_CODE(3011,"Discount invalid",HttpStatus.BAD_REQUEST),
    INVALID_ORDER_AMOUNT(3012,"Invalid order amount",HttpStatus.BAD_REQUEST),
    PAYMENT_NOT_REFUNDABLE(3013,"This payment cannot be refunded.",HttpStatus.BAD_REQUEST),
    INVALID_REFUND_AMOUNT(3014, "Invalid refund amount.",HttpStatus.BAD_REQUEST),
    JSON_PARSE_ERROR(3015,"Invalid or unreadable JSON format in request body.",HttpStatus.BAD_REQUEST),
    // ===== Resource not found =====
    USER_NOT_FOUND(4001, "User not found", HttpStatus.NOT_FOUND),
    PRODUCT_NOT_FOUND(4002, "Product not found", HttpStatus.NOT_FOUND),
    ORDER_NOT_FOUND(4003, "Order not found", HttpStatus.NOT_FOUND),
    CART_NOT_FOUND(4004, "Cart not found", HttpStatus.NOT_FOUND),
    CATEGORY_NOT_FOUND(4005, "Category not found", HttpStatus.NOT_FOUND),
    SUPPLIER_NOT_FOUND(4006, "Supplier not found", HttpStatus.NOT_FOUND),
    ROLE_NOT_FOUND(4007, "Role not found", HttpStatus.NOT_FOUND),
    INVALID_STATUS(4008, "INVALID STATUS", HttpStatus.NOT_FOUND),
    IMPORT_ORDER_NOT_FOUND(4009, "Import Order not found", HttpStatus.NOT_FOUND),
    DISCOUNT_NOT_FOUND_MESSAGE(4010, "Discount not found with id:", HttpStatus.NOT_FOUND),
    DISCOUNT_NOT_ACTIVE(4011,"Discount not active",HttpStatus.NOT_FOUND),
    DISCOUNT_NOT_FOUND(4012,"Discount not found",HttpStatus.NOT_FOUND),
    NOT_ENOUGH_POINTS(4013,"Not enough points to use",HttpStatus.NOT_FOUND),
    PAYMENT_NOT_FOUND(4014,"Payment not found",HttpStatus.NOT_FOUND),
    // ===== Business rules =====
    ORDER_ALREADY_SHIPPED(5001, "Cannot delete a shipped order", HttpStatus.BAD_REQUEST),
    CANNOT_CANCEL_ORDER(5002, "Cannot cancel this order in its current state", HttpStatus.CONFLICT),
    SUPPLIER_NAME_ALREADY_EXISTS(5003, "Supplier name already exists", HttpStatus.BAD_REQUEST),
    DUPLICATE_RESOURCE(5004,"Repeat data.",HttpStatus.CONFLICT),
    COUPON_NOT_EXISTS(5005,"Coupon code does not exist:",HttpStatus.CONFLICT),
    PAYMENT_ALREADY_EXISTS(5006, "Payment already exists", HttpStatus.BAD_REQUEST),
    USERNAME_ALREADY_EXISTS(5007, "Username already exists", HttpStatus.BAD_REQUEST),
    ORDER_NOT_EXISTED(5008, "Order not existed", HttpStatus.CONFLICT),
    NAME_SUPPLIER_NOT_EXISTS(5009, "Name supplier not exists", HttpStatus.CONFLICT);

    private final int code;
    private final String message;
    private final HttpStatus statusCode;
}
