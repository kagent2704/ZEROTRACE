package com.zerotrace.model;

public class MessagePacket {

    private Long id;
    private String sender;
    private String receiver;
    private String encryptedMessage;
    private String encryptedAESKey;
    private String signature;
    private String iv;
    private String mode;
    private Integer ttlSeconds;
    private String createdAt;

    public MessagePacket() {
    }

    public MessagePacket(
            Long id,
            String sender,
            String receiver,
            String encryptedMessage,
            String encryptedAESKey,
            String signature,
            String iv,
            String mode,
            Integer ttlSeconds,
            String createdAt
    ) {
        this.id = id;
        this.sender = sender;
        this.receiver = receiver;
        this.encryptedMessage = encryptedMessage;
        this.encryptedAESKey = encryptedAESKey;
        this.signature = signature;
        this.iv = iv;
        this.mode = mode;
        this.ttlSeconds = ttlSeconds;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getEncryptedMessage() {
        return encryptedMessage;
    }

    public String getEncryptedAESKey() {
        return encryptedAESKey;
    }

    public String getSignature() {
        return signature;
    }

    public String getIv() {
        return iv;
    }

    public String getMode() {
        return mode;
    }

    public Integer getTtlSeconds() {
        return ttlSeconds;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
