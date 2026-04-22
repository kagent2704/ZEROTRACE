package com.zerotrace.auth_server.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "encrypted_messages")
public class EncryptedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sender;

    @Column(nullable = false)
    private String receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageMode mode = MessageMode.PRIVATE;

    @Lob
    @Column(nullable = false, length = 8192)
    private String encryptedMessage;

    @Lob
    @Column(nullable = false, length = 4096)
    private String encryptedAESKey;

    @Lob
    @Column(nullable = false, length = 4096)
    private String signature;

    @Column(nullable = false, length = 512)
    private String iv;

    @Column(nullable = false)
    private Integer messageSize;

    private Integer ttlSeconds;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant deliveredAt;

    private Instant expiresAt;

    private Instant auditRetainUntil;
}
