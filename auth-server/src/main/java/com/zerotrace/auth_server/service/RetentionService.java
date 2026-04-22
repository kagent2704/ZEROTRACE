package com.zerotrace.auth_server.service;

import com.zerotrace.auth_server.model.MessageMode;
import com.zerotrace.auth_server.repository.AuditExportRequestRepository;
import com.zerotrace.auth_server.repository.EncryptedMessageRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class RetentionService {

    private final EncryptedMessageRepository encryptedMessageRepository;
    private final AuditExportRequestRepository auditExportRequestRepository;

    public RetentionService(
            EncryptedMessageRepository encryptedMessageRepository,
            AuditExportRequestRepository auditExportRequestRepository
    ) {
        this.encryptedMessageRepository = encryptedMessageRepository;
        this.auditExportRequestRepository = auditExportRequestRepository;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void purgeExpiredData() {
        Instant now = Instant.now();

        encryptedMessageRepository.deleteByModeAndExpiresAtBefore(MessageMode.PRIVATE, now);
        encryptedMessageRepository.deleteByModeAndAuditRetainUntilBefore(MessageMode.AUDIT, now);

        auditExportRequestRepository.findByExpiredFalseAndExpiresAtBefore(now).forEach(request -> {
            request.setExpired(true);
            request.setApproved(false);
            auditExportRequestRepository.save(request);
        });
    }
}
