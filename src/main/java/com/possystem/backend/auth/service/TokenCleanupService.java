package com.possystem.backend.auth.service;

public interface TokenCleanupService {
    void cleanUpExpiredTokens();
}
