package com.zerotrace.auth_server.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageRequest {
    private String sender;
    private String receiver;
    private String encryptedMessage;
    private String encryptedAESKey;
    private String signature;
    private String iv;
    private String mode;
    private Integer ttlSeconds;
}
