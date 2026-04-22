package com.zerotrace.auth_server.repository;

import com.zerotrace.auth_server.model.AuditExportRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AuditExportRequestRepository extends JpaRepository<AuditExportRequestEntity, Long> {
    List<AuditExportRequestEntity> findByOrganizationAndExpiredFalseAndApprovedFalseOrderByRequestedAtAsc(String organization);

    List<AuditExportRequestEntity> findByExpiredFalseAndExpiresAtBefore(Instant expiresAt);

    List<AuditExportRequestEntity> findByUsernameOrderByRequestedAtDesc(String username);
}
