package com.zerotrace.auth_server.repository;

import com.zerotrace.auth_server.model.EncryptedMessage;
import com.zerotrace.auth_server.model.MessageMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface EncryptedMessageRepository extends JpaRepository<EncryptedMessage, Long> {
    List<EncryptedMessage> findByReceiverAndDeliveredAtIsNullOrderByCreatedAtAsc(String receiver);

    long countBySenderAndCreatedAtAfter(String sender, Instant createdAt);

    @Query("""
            select count(distinct m.receiver)
            from EncryptedMessage m
            where m.sender = :sender and m.createdAt >= :createdAt
            """)
    long countDistinctReceiversBySenderAndCreatedAtAfter(
            @Param("sender") String sender,
            @Param("createdAt") Instant createdAt
    );

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

    @Query("""
            select m
            from EncryptedMessage m
            where m.receiver = :receiver
              and m.deliveredAt is not null
              and (
                    (m.mode = com.zerotrace.auth_server.model.MessageMode.AUDIT
                     and (m.auditRetainUntil is null or m.auditRetainUntil > :now))
                 or (m.mode = com.zerotrace.auth_server.model.MessageMode.PRIVATE
                     and (m.expiresAt is null or m.expiresAt > :now))
              )
            order by m.createdAt asc
            """)
    List<EncryptedMessage> findVisibleHistoryForReceiver(
            @Param("receiver") String receiver,
            @Param("now") Instant now
    );

    long deleteByModeAndExpiresAtBefore(MessageMode mode, Instant expiresAt);

    long deleteByModeAndAuditRetainUntilBefore(MessageMode mode, Instant retainUntil);
}
