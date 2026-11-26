package com.possystem.backend.common.outbox.event;

/**
 * Canonical business event types emitted by Đẳng Cấp Coffee POS.
 */
public final class PosEventType {

    private PosEventType() {
    }

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_COMPLETED = "OrderCompleted";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String PAYMENT_COMPLETED = "PaymentCompleted";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String INVENTORY_UPDATED = "InventoryUpdated";
    public static final String VOUCHER_REDEEMED = "VoucherRedeemed";
    public static final String LOYALTY_POINTS_EARNED = "LoyaltyPointsEarned";
    public static final String SHIFT_CLOSED = "ShiftClosed";

    // Legacy event aliases preserved for backward compatibility
    public static final String LEGACY_ORDER_CREATED = "ORDER_CREATED";
    public static final String LEGACY_PAYMENT_SUCCESS = "PAYMENT_SUCCESS";
}
