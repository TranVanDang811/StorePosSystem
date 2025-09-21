package com.possystem.backend.auth.service.impl;

import com.possystem.backend.auth.repository.InvalidatedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Date;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenCleanupServiceImpl {
    private final InvalidatedTokenRepository invalidatedTokenRepository;

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanUpExpiredTokens() {
        Date now = new Date();
        int deletedCount = invalidatedTokenRepository.deleteAllByExpiryTimeBefore(now);
        log.info("Deleted {} expired invalidated tokens", deletedCount);
    }
}
