package com.zerotrace.auth_server.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class MessagePacket {

    private Long id;
    private String sender;
    private String receiver;
    private String encryptedMessage;
    private String encryptedAESKey;
    private String signature;
    private String iv;
    private MessageMode mode;
    private Integer ttlSeconds;
    private Instant createdAt;
    private Instant deliveredAt;
}
