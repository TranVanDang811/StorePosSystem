package com.possystem.backend.common.outbox.consumer;

import com.possystem.backend.common.outbox.event.DomainEventEnvelope;
import com.possystem.backend.common.outbox.event.PosEventType;
import com.possystem.backend.user.entity.CustomerProfile;
import com.possystem.backend.user.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Performs post-payment loyalty processing asynchronously (e.g. membership tier evaluation),
 * decoupled from critical checkout transaction latency.
 */
@Slf4j
@Order(4)
@Component
@RequiredArgsConstructor
public class LoyaltyPostProcessingConsumer implements PosEventConsumer {

    private final CustomerProfileRepository customerProfileRepository;

    @Override
    public String consumerName() {
        return "LoyaltyPostProcessingConsumer";
    }

    @Override
    public boolean supports(String eventType) {
        return PosEventType.LOYALTY_POINTS_EARNED.equals(eventType);
    }

    @Override
    public void consume(DomainEventEnvelope envelope) {
        Map<String, Object> payload = envelope.getPayload() != null ? envelope.getPayload() : Map.of();
        Object profileIdObj = payload.get("customerProfileId");
        if (profileIdObj == null) {
            return;
        }

        String profileId = String.valueOf(profileIdObj);
        customerProfileRepository.findByIdWithLock(profileId).ifPresent(profile -> {
            String newTier = resolveMembershipTier(profile.getLoyaltyPoints());
            if (!newTier.equals(profile.getMembershipLevel())) {
                String oldTier = profile.getMembershipLevel();
                profile.setMembershipLevel(newTier);
                customerProfileRepository.save(profile);
                log.info("Loyalty tier upgraded asynchronously for customerProfile {}: {} -> {} (points={})",
                        profileId, oldTier, newTier, profile.getLoyaltyPoints());
            }
        });
    }

    static String resolveMembershipTier(int points) {
        if (points >= 2000) return "PLATINUM";
        if (points >= 500) return "GOLD";
        if (points >= 100) return "SILVER";
        return "MEMBER";
    }
}
