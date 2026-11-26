package com.possystem.backend.common.cache;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized Redis cache region names used across Đẳng Cấp Coffee POS.
 * Note: Payment execution, inventory deduction, loyalty balances, and active voucher
 * quota checks NEVER rely on Redis as their source of truth.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CacheNames {
    public static final String PRODUCTS_LIST = "pos:products:list";
    public static final String PRODUCTS_DETAIL = "pos:products:detail";
    public static final String PRODUCTS_STATS = "pos:products:stats";
    public static final String PRODUCTS_BY_SUPPLIER = "pos:products:by-supplier";

    public static final String CATEGORIES_LIST = "pos:categories:list";
    public static final String CATEGORIES_DETAIL = "pos:categories:detail";
    public static final String CATEGORIES_SUMMARY = "pos:categories:summary";

    public static final String SUPPLIERS_LIST = "pos:suppliers:list";
    public static final String SUPPLIERS_DETAIL = "pos:suppliers:detail";
    public static final String SUPPLIERS_SUMMARY = "pos:suppliers:summary";

    public static final String DISCOUNTS_ACTIVE = "pos:discounts:active";
    public static final String DISCOUNTS_ALL = "pos:discounts:all";

    public static final String REPORTS_DASHBOARD = "pos:reports:dashboard";
    public static final String REPORTS_DAILY = "pos:reports:daily";
    public static final String REPORTS_MONTHLY = "pos:reports:monthly";
    public static final String REPORTS_PRODUCT_SALES = "pos:reports:product-sales";
}
