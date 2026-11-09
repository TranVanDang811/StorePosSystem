package com.possystem.backend.user.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerHistoryResponse {
    private long totalOrders;
    private BigDecimal totalSpent;
    private LocalDateTime lastPurchaseDate;
    private int loyaltyPoints;
    private String membershipLevel;

    public CustomerHistoryResponse() {}

    public CustomerHistoryResponse(long totalOrders, BigDecimal totalSpent, LocalDateTime lastPurchaseDate, int loyaltyPoints, String membershipLevel) {
        this.totalOrders = totalOrders;
        this.totalSpent = totalSpent;
        this.lastPurchaseDate = lastPurchaseDate;
        this.loyaltyPoints = loyaltyPoints;
        this.membershipLevel = membershipLevel;
    }

    public long getTotalOrders() {
        return totalOrders;
    }

    public void setTotalOrders(long totalOrders) {
        this.totalOrders = totalOrders;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public void setTotalSpent(BigDecimal totalSpent) {
        this.totalSpent = totalSpent;
    }

    public LocalDateTime getLastPurchaseDate() {
        return lastPurchaseDate;
    }

    public void setLastPurchaseDate(LocalDateTime lastPurchaseDate) {
        this.lastPurchaseDate = lastPurchaseDate;
    }

    public int getLoyaltyPoints() {
        return loyaltyPoints;
    }

    public void setLoyaltyPoints(int loyaltyPoints) {
        this.loyaltyPoints = loyaltyPoints;
    }

    public String getMembershipLevel() {
        return membershipLevel;
    }

    public void setMembershipLevel(String membershipLevel) {
        this.membershipLevel = membershipLevel;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long totalOrders;
        private BigDecimal totalSpent;
        private LocalDateTime lastPurchaseDate;
        private int loyaltyPoints;
        private String membershipLevel;

        public Builder totalOrders(long totalOrders) {
            this.totalOrders = totalOrders;
            return this;
        }

        public Builder totalSpent(BigDecimal totalSpent) {
            this.totalSpent = totalSpent;
            return this;
        }

        public Builder lastPurchaseDate(LocalDateTime lastPurchaseDate) {
            this.lastPurchaseDate = lastPurchaseDate;
            return this;
        }

        public Builder loyaltyPoints(int loyaltyPoints) {
            this.loyaltyPoints = loyaltyPoints;
            return this;
        }

        public Builder membershipLevel(String membershipLevel) {
            this.membershipLevel = membershipLevel;
            return this;
        }

        public CustomerHistoryResponse build() {
            return new CustomerHistoryResponse(totalOrders, totalSpent, lastPurchaseDate, loyaltyPoints, membershipLevel);
        }
    }
}
