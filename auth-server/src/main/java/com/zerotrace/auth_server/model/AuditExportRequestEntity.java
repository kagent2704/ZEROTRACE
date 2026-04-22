package com.zerotrace.auth_server.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "audit_export_requests")
public class AuditExportRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String organization;

    @Column(nullable = false)
    private Instant requestedAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant approvedAt;

    @Column(nullable = false)
    private boolean approved = false;

    @Column(nullable = false)
    private boolean expired = false;

    @Column(nullable = false)
    private Integer lookbackDays = 7;
}
