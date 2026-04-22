package com.zerotrace.auth_server.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String organization = "default-org";

    @Lob
    @Column(nullable = false, length = 4096)
    private String publicKey;

    @Column(nullable = false)
    private String role = "USER";

    @Column(nullable = false)
    private boolean auditModeApproved = false;

    @Column(nullable = false)
    private boolean exportApproved = false;

    private Integer failedLoginAttempts = 0;

    private String lastKnownIp;

    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;
}
