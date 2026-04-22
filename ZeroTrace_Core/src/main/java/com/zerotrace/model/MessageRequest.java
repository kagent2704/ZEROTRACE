package com.zerotrace.model;

public class MessageRequest {
    private final String sender;
    private final String receiver;
    private final String encryptedMessage;
    private final String encryptedAESKey;
    private final String signature;
    private final String iv;
    private final String mode;
    private final Integer ttlSeconds;

    public MessageRequest(
            String sender,
            String receiver,
            String encryptedMessage,
            String encryptedAESKey,
            String signature,
            String iv,
            String mode,
            Integer ttlSeconds
    ) {
        this.sender = sender;
        this.receiver = receiver;
        this.encryptedMessage = encryptedMessage;
        this.encryptedAESKey = encryptedAESKey;
        this.signature = signature;
        this.iv = iv;
        this.mode = mode;
        this.ttlSeconds = ttlSeconds;
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
}
