package com.possystem.backend.ai.repository;

import com.possystem.backend.ai.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessage, String> {

    List<AiMessage> findByConversation_IdOrderByCreatedAtAsc(String conversationId);

    long countByConversation_Id(String conversationId);
}
