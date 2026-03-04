package org.jume.loyalitybot.repository;

import org.jume.loyalitybot.model.ChatMessage;
import org.jume.loyalitybot.model.ChatMessage.SenderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    List<ChatMessage> findByCustomerIdOrderByCreatedAtAsc(Long customerId);

    Optional<ChatMessage> findByForwardedMessageIdAndAdminChatId(Long forwardedMessageId, Long adminChatId);

    long countBySenderTypeAndReadAtIsNull(SenderType senderType);

    @Modifying
    @Query("UPDATE ChatMessage cm SET cm.readAt = :readAt WHERE cm.customer.id = :customerId AND cm.senderType = :senderType AND cm.readAt IS NULL")
    int markMessagesAsRead(@Param("customerId") Long customerId, @Param("senderType") SenderType senderType, @Param("readAt") LocalDateTime readAt);
}
