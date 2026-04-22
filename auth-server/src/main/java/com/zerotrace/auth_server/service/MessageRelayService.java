package com.zerotrace.auth_server.service;

import com.zerotrace.auth_server.model.EncryptedMessage;
import com.zerotrace.auth_server.model.MessageMode;
import com.zerotrace.auth_server.model.MessagePacket;
import com.zerotrace.auth_server.model.MessageRequest;
import com.zerotrace.auth_server.model.SendMessageResponse;
import com.zerotrace.auth_server.model.ThreatAnalysisResult;
import com.zerotrace.auth_server.model.User;
import com.zerotrace.auth_server.repository.EncryptedMessageRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class MessageRelayService {

    private final EncryptedMessageRepository messageRepository;
    private final UserService userService;
    private final ThreatDetectionService threatDetectionService;

    public MessageRelayService(
            EncryptedMessageRepository messageRepository,
            UserService userService,
            ThreatDetectionService threatDetectionService
    ) {
        this.messageRepository = messageRepository;
        this.userService = userService;
        this.threatDetectionService = threatDetectionService;
    }

    public SendMessageResponse relay(String authenticatedUsername, MessageRequest request, HttpServletRequest httpRequest) {
        validateRequest(request);
        if (!authenticatedUsername.equals(request.getSender())) {
            throw new IllegalArgumentException("Sender does not match authenticated user");
        }

        User sender = userService.getUser(request.getSender());
        User receiver = userService.getUser(request.getReceiver());
        MessageMode mode = parseMode(request.getMode());
        validateGovernance(sender, receiver, mode);

        String incomingIp = resolveClientIp(httpRequest);
        int ipChanges = sender.getLastKnownIp() != null && !sender.getLastKnownIp().equals(incomingIp) ? 1 : 0;
        sender.setLastKnownIp(incomingIp);

        int messageSize = request.getEncryptedMessage().length();
        ThreatAnalysisResult analysis = threatDetectionService.analyzeOutboundTraffic(sender, messageSize, ipChanges);

        EncryptedMessage message = new EncryptedMessage();
        message.setSender(request.getSender());
        message.setReceiver(request.getReceiver());
        message.setEncryptedMessage(request.getEncryptedMessage());
        message.setEncryptedAESKey(request.getEncryptedAESKey());
        message.setSignature(request.getSignature());
        message.setIv(request.getIv());
        message.setMessageSize(messageSize);
        message.setMode(mode);
        message.setTtlSeconds(normalizeTtl(request.getTtlSeconds(), mode));
        if (mode == MessageMode.PRIVATE) {
            message.setExpiresAt(Instant.now().plusSeconds(message.getTtlSeconds()));
        } else {
            message.setAuditRetainUntil(Instant.now().plus(7, ChronoUnit.DAYS));
        }

        EncryptedMessage saved = messageRepository.save(message);
        return new SendMessageResponse(saved.getId(), mode, analysis);
    }

    public List<MessagePacket> fetchInbox(String authenticatedUsername, String username) {
        if (!authenticatedUsername.equals(username)) {
            throw new IllegalArgumentException("Inbox access denied for this user");
        }
        userService.getUser(username);
        Instant now = Instant.now();
        List<EncryptedMessage> messages = messageRepository
                .findByReceiverAndDeliveredAtIsNullAndExpiresAtAfterOrReceiverAndDeliveredAtIsNullAndExpiresAtIsNullOrderByCreatedAtAsc(
                        username,
                        now,
                        username
                )
                .stream()
                .filter(message -> message.getMode() == MessageMode.AUDIT
                        || message.getExpiresAt() == null
                        || message.getExpiresAt().isAfter(now))
                .toList();
        Instant deliveredAt = Instant.now();

        messages.forEach(message -> message.setDeliveredAt(deliveredAt));
        messageRepository.saveAll(messages);

        return messages.stream()
                .map(message -> new MessagePacket(
                        message.getId(),
                        message.getSender(),
                        message.getReceiver(),
                        message.getEncryptedMessage(),
                        message.getEncryptedAESKey(),
                        message.getSignature(),
                        message.getIv(),
                        message.getMode(),
                        message.getTtlSeconds(),
                        message.getCreatedAt()
                ))
                .toList();
    }

    private void validateRequest(MessageRequest request) {
        if (isBlank(request.getSender()) || isBlank(request.getReceiver())) {
            throw new IllegalArgumentException("Sender and receiver are required");
        }
        if (isBlank(request.getEncryptedMessage())
                || isBlank(request.getEncryptedAESKey())
                || isBlank(request.getSignature())
                || isBlank(request.getIv())) {
            throw new IllegalArgumentException("Encrypted payload is incomplete");
        }
    }

    private MessageMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MessageMode.PRIVATE;
        }
        return MessageMode.valueOf(mode.toUpperCase());
    }

    private Integer normalizeTtl(Integer ttlSeconds, MessageMode mode) {
        if (mode == MessageMode.AUDIT) {
            return null;
        }
        int ttl = ttlSeconds == null ? 60 : ttlSeconds;
        return Math.max(5, Math.min(ttl, 86400));
    }

    private void validateGovernance(User sender, User receiver, MessageMode mode) {
        if (!sender.getOrganization().equals(receiver.getOrganization())) {
            throw new IllegalArgumentException("Cross-organization messaging is not allowed");
        }

        if (mode == MessageMode.AUDIT) {
            if (!sender.isAuditModeApproved() || !receiver.isAuditModeApproved()) {
                throw new IllegalArgumentException("Audit mode is not approved for both users");
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
