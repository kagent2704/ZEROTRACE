package com.zerotrace.auth_server.repository;

import com.zerotrace.auth_server.model.EncryptedMessage;
import com.zerotrace.auth_server.model.MessageMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EncryptedMessageRepository extends JpaRepository<EncryptedMessage, Long> {
    List<EncryptedMessage> findByReceiverAndDeliveredAtIsNullOrderByCreatedAtAsc(String receiver);

    long countBySenderAndCreatedAtAfter(String sender, Instant createdAt);

    List<EncryptedMessage> findByReceiverAndDeliveredAtIsNullAndExpiresAtAfterOrReceiverAndDeliveredAtIsNullAndExpiresAtIsNullOrderByCreatedAtAsc(
            String receiver,
            Instant now,
            String receiverAgain
    );

    List<EncryptedMessage> findByReceiverAndModeAndCreatedAtAfterOrderByCreatedAtAsc(
            String receiver,
            MessageMode mode,
            Instant createdAt
    );

    long deleteByModeAndExpiresAtBefore(MessageMode mode, Instant expiresAt);

    long deleteByModeAndAuditRetainUntilBefore(MessageMode mode, Instant retainUntil);
}
