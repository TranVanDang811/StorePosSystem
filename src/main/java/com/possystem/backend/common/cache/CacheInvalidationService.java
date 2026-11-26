package com.possystem.backend.common.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Domain-driven cache invalidation service.
 * Schedules cache evictions AFTER database transaction commit when inside an active transaction
 * to prevent uncommitted-read cache poisoning and avoid clearing caches on transaction rollback.
 */
@Slf4j
@Service
public class CacheInvalidationService {

    private final PosCacheService posCacheService;

    public CacheInvalidationService(PosCacheService posCacheService) {
        this.posCacheService = posCacheService;
    }

    public void evictProductCaches() {
        runAfterCommit(() -> {
            posCacheService.evictAll(CacheNames.PRODUCTS_LIST);
            posCacheService.evictAll(CacheNames.PRODUCTS_DETAIL);
            posCacheService.evictAll(CacheNames.PRODUCTS_STATS);
            posCacheService.evictAll(CacheNames.PRODUCTS_BY_SUPPLIER);
            posCacheService.evictAll(CacheNames.CATEGORIES_SUMMARY);
            posCacheService.evictAll(CacheNames.CATEGORIES_LIST);
            posCacheService.evictAll(CacheNames.SUPPLIERS_SUMMARY);
            posCacheService.evictAll(CacheNames.SUPPLIERS_LIST);
            posCacheService.evictAll(CacheNames.REPORTS_DASHBOARD);
        });
    }

    public void evictCategoryCaches() {
        runAfterCommit(() -> {
            posCacheService.evictAll(CacheNames.CATEGORIES_LIST);
            posCacheService.evictAll(CacheNames.CATEGORIES_DETAIL);
            posCacheService.evictAll(CacheNames.CATEGORIES_SUMMARY);
            posCacheService.evictAll(CacheNames.PRODUCTS_LIST);
            posCacheService.evictAll(CacheNames.PRODUCTS_DETAIL);
        });
    }

    public void evictSupplierCaches() {
        runAfterCommit(() -> {
            posCacheService.evictAll(CacheNames.SUPPLIERS_LIST);
            posCacheService.evictAll(CacheNames.SUPPLIERS_DETAIL);
            posCacheService.evictAll(CacheNames.SUPPLIERS_SUMMARY);
            posCacheService.evictAll(CacheNames.PRODUCTS_LIST);
            posCacheService.evictAll(CacheNames.PRODUCTS_DETAIL);
            posCacheService.evictAll(CacheNames.PRODUCTS_BY_SUPPLIER);
        });
    }

    public void evictDiscountCaches() {
        runAfterCommit(() -> {
            posCacheService.evictAll(CacheNames.DISCOUNTS_ACTIVE);
            posCacheService.evictAll(CacheNames.DISCOUNTS_ALL);
        });
    }

    public void evictReportCaches() {
        runAfterCommit(() -> {
            posCacheService.evictAll(CacheNames.REPORTS_DASHBOARD);
            posCacheService.evictAll(CacheNames.REPORTS_DAILY);
            posCacheService.evictAll(CacheNames.REPORTS_MONTHLY);
            posCacheService.evictAll(CacheNames.REPORTS_PRODUCT_SALES);
        });
    }

    public void evictAfterOrderMutation(boolean discountUsed) {
        runAfterCommit(() -> {
            posCacheService.evictAll(CacheNames.REPORTS_DASHBOARD);
            if (discountUsed) {
                posCacheService.evictAll(CacheNames.DISCOUNTS_ACTIVE);
                posCacheService.evictAll(CacheNames.DISCOUNTS_ALL);
            }
        });
    }

    public void evictAfterSaleOrRefund() {
        runAfterCommit(() -> {
            posCacheService.evictAll(CacheNames.PRODUCTS_LIST);
            posCacheService.evictAll(CacheNames.PRODUCTS_DETAIL);
            posCacheService.evictAll(CacheNames.PRODUCTS_STATS);
            posCacheService.evictAll(CacheNames.PRODUCTS_BY_SUPPLIER);
            posCacheService.evictAll(CacheNames.REPORTS_DASHBOARD);
            posCacheService.evictAll(CacheNames.REPORTS_DAILY);
            posCacheService.evictAll(CacheNames.REPORTS_MONTHLY);
            posCacheService.evictAll(CacheNames.REPORTS_PRODUCT_SALES);
        });
    }

    public void evictAfterImportFinalized() {
        runAfterCommit(() -> {
            posCacheService.evictAll(CacheNames.PRODUCTS_LIST);
            posCacheService.evictAll(CacheNames.PRODUCTS_DETAIL);
            posCacheService.evictAll(CacheNames.PRODUCTS_STATS);
            posCacheService.evictAll(CacheNames.PRODUCTS_BY_SUPPLIER);
            posCacheService.evictAll(CacheNames.SUPPLIERS_SUMMARY);
            posCacheService.evictAll(CacheNames.SUPPLIERS_LIST);
            posCacheService.evictAll(CacheNames.REPORTS_DASHBOARD);
        });
    }

    public void evictAllCaches() {
        posCacheService.evictAll(CacheNames.PRODUCTS_LIST);
        posCacheService.evictAll(CacheNames.PRODUCTS_DETAIL);
        posCacheService.evictAll(CacheNames.PRODUCTS_STATS);
        posCacheService.evictAll(CacheNames.PRODUCTS_BY_SUPPLIER);
        posCacheService.evictAll(CacheNames.CATEGORIES_LIST);
        posCacheService.evictAll(CacheNames.CATEGORIES_DETAIL);
        posCacheService.evictAll(CacheNames.CATEGORIES_SUMMARY);
        posCacheService.evictAll(CacheNames.SUPPLIERS_LIST);
        posCacheService.evictAll(CacheNames.SUPPLIERS_DETAIL);
        posCacheService.evictAll(CacheNames.SUPPLIERS_SUMMARY);
        posCacheService.evictAll(CacheNames.DISCOUNTS_ACTIVE);
        posCacheService.evictAll(CacheNames.DISCOUNTS_ALL);
        posCacheService.evictAll(CacheNames.REPORTS_DASHBOARD);
        posCacheService.evictAll(CacheNames.REPORTS_DAILY);
        posCacheService.evictAll(CacheNames.REPORTS_MONTHLY);
        posCacheService.evictAll(CacheNames.REPORTS_PRODUCT_SALES);
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeExecute(action);
                }
            });
        } else {
            safeExecute(action);
        }
    }

    private void safeExecute(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("Post-commit cache invalidation encountered non-fatal error: {}", ex.getMessage());
        }
    }
}
