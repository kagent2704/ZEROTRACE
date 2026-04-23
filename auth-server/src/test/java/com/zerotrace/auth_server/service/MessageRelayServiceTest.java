package com.zerotrace.auth_server.service;

import com.zerotrace.auth_server.model.EncryptedMessage;
import com.zerotrace.auth_server.model.MessageMode;
import com.zerotrace.auth_server.model.MessagePacket;
import com.zerotrace.auth_server.repository.EncryptedMessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessageRelayServiceTest {

    @Test
    void fetchInboxStartsPrivateTtlWhenMessageIsDelivered() {
        EncryptedMessageRepository messageRepository = mock(EncryptedMessageRepository.class);
        UserService userService = mock(UserService.class);
        ThreatDetectionService threatDetectionService = mock(ThreatDetectionService.class);
        MessageRelayService service = new MessageRelayService(messageRepository, userService, threatDetectionService);

        Instant now = Instant.now();
        EncryptedMessage active = privateMessage("alice", "bob", now.minusSeconds(120), 60);
        EncryptedMessage audit = auditMessage("carol", "bob", now.minusSeconds(30));

        when(messageRepository.findByReceiverAndDeliveredAtIsNullAndExpiresAtAfterOrReceiverAndDeliveredAtIsNullAndExpiresAtIsNullOrderByCreatedAtAsc(
                any(), any(), any()
        )).thenReturn(List.of(active, audit));
        when(messageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<MessagePacket> inbox = service.fetchInbox("bob", "bob");

        assertEquals(2, inbox.size());
        assertEquals("alice", inbox.get(0).getSender());
        assertEquals(60, inbox.get(0).getTtlSeconds());
        assertEquals(true, inbox.get(0).getDeliveredAt() != null);
        assertEquals("carol", inbox.get(1).getSender());
        assertNull(inbox.get(1).getTtlSeconds());
        verify(userService).getUser("bob");
        verify(messageRepository).saveAll(any());
    }

    @Test
    void fetchInboxDeliversUndeliveredPrivateMessageEvenIfItWasCreatedLongAgo() {
        EncryptedMessageRepository messageRepository = mock(EncryptedMessageRepository.class);
        UserService userService = mock(UserService.class);
        ThreatDetectionService threatDetectionService = mock(ThreatDetectionService.class);
        MessageRelayService service = new MessageRelayService(messageRepository, userService, threatDetectionService);

        Instant now = Instant.now();
        EncryptedMessage pending = privateMessage("alice", "bob", now.minusSeconds(180), 30);

        when(messageRepository.findByReceiverAndDeliveredAtIsNullAndExpiresAtAfterOrReceiverAndDeliveredAtIsNullAndExpiresAtIsNullOrderByCreatedAtAsc(
                any(), any(), any()
        )).thenReturn(List.of(pending));
        when(messageRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<MessagePacket> inbox = service.fetchInbox("bob", "bob");

        assertEquals(1, inbox.size());
        assertEquals("alice", inbox.get(0).getSender());
        verify(messageRepository).saveAll(any());
    }

    private static EncryptedMessage privateMessage(String sender, String receiver, Instant createdAt, int ttlSeconds) {
        EncryptedMessage message = new EncryptedMessage();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setMode(MessageMode.PRIVATE);
        message.setEncryptedMessage("cipher");
        message.setEncryptedAESKey("aes");
        message.setSignature("sig");
        message.setIv("iv");
        message.setMessageSize(16);
        message.setTtlSeconds(ttlSeconds);
        message.setCreatedAt(createdAt);
        message.setExpiresAt(null);
        return message;
    }

    private static EncryptedMessage auditMessage(String sender, String receiver, Instant createdAt) {
        EncryptedMessage message = new EncryptedMessage();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setMode(MessageMode.AUDIT);
        message.setEncryptedMessage("cipher");
        message.setEncryptedAESKey("aes");
        message.setSignature("sig");
        message.setIv("iv");
        message.setMessageSize(16);
        message.setCreatedAt(createdAt);
        return message;
    }
}
