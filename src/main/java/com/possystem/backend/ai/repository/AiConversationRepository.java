package com.possystem.backend.ai.repository;

import com.possystem.backend.ai.entity.AiConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversation, String> {

    List<AiConversation> findByUserIdAndArchivedFalseOrderByCreatedAtDesc(String userId);

    Page<AiConversation> findByUserIdAndArchivedFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    Optional<AiConversation> findByIdAndUserId(String id, String userId);
}
