package com.dbperf.copilot.repository;

import com.dbperf.copilot.domain.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    List<ChatMessage> findAllBySessionIdOrderByCreatedAtAsc(UUID sessionId);
}
