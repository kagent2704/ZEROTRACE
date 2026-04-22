package com.zerotrace.auth_server.service;

import com.zerotrace.auth_server.model.*;
import com.zerotrace.auth_server.repository.AuditExportRequestRepository;
import com.zerotrace.auth_server.repository.EncryptedMessageRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class GovernanceService {

    private final UserService userService;
    private final AuditExportRequestRepository exportRequestRepository;
    private final EncryptedMessageRepository encryptedMessageRepository;

    public GovernanceService(
            UserService userService,
            AuditExportRequestRepository exportRequestRepository,
            EncryptedMessageRepository encryptedMessageRepository
    ) {
        this.userService = userService;
        this.exportRequestRepository = exportRequestRepository;
        this.encryptedMessageRepository = encryptedMessageRepository;
    }

    public void setAuditApproval(String adminUsername, String targetUsername, boolean approved) {
        User admin = userService.getUser(adminUsername);
        userService.requireOrgAdmin(adminUsername);
        User target = userService.getUser(targetUsername);
        requireSameOrg(admin, target);
        target.setAuditModeApproved(approved);
        target.setExportApproved(approved);
        userService.save(target);
    }

    public AuditExportResponse createExportRequest(String username, int lookbackDays) {
        User user = userService.getUser(username);
        if (!user.isAuditModeApproved()) {
            throw new IllegalArgumentException("Audit mode is not approved for this user");
        }

        AuditExportRequestEntity request = new AuditExportRequestEntity();
        request.setUsername(username);
        request.setOrganization(user.getOrganization());
        request.setLookbackDays(Math.max(1, Math.min(lookbackDays, 7)));
        request.setExpiresAt(Instant.now().plus(48, ChronoUnit.HOURS));
        AuditExportRequestEntity saved = exportRequestRepository.save(request);
        return toResponse(saved, "Awaiting admin approval");
    }

    public void approveExportRequest(String adminUsername, Long requestId, boolean approved) {
        User admin = userService.getUser(adminUsername);
        userService.requireOrgAdmin(adminUsername);

        AuditExportRequestEntity request = exportRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Export request not found"));

        if (!admin.getOrganization().equals(request.getOrganization())) {
            throw new IllegalArgumentException("Cross-organization approval is not allowed");
        }

        expireIfNeeded(request);
        if (request.isExpired()) {
            throw new IllegalArgumentException("Export request has expired");
        }

        request.setApproved(approved);
        request.setApprovedAt(approved ? Instant.now() : null);
        request.setExpired(!approved);
        exportRequestRepository.save(request);
    }

    public List<AuditExportResponse> pendingExportRequests(String adminUsername) {
        User admin = userService.getUser(adminUsername);
        userService.requireOrgAdmin(adminUsername);

        return exportRequestRepository.findByOrganizationAndExpiredFalseAndApprovedFalseOrderByRequestedAtAsc(admin.getOrganization())
                .stream()
                .peek(this::expireIfNeeded)
                .filter(request -> !request.isExpired())
                .map(request -> toResponse(
                        request,
                        request.getUsername() + " requested " + request.getLookbackDays() + " day(s) of audit export"
                ))
                .toList();
    }

    public List<AuditExportResponse> exportRequestsForUser(String username) {
        return exportRequestRepository.findByUsernameOrderByRequestedAtDesc(username)
                .stream()
                .peek(this::expireIfNeeded)
                .map(request -> toResponse(request, "Requested " + request.getLookbackDays() + " day(s) of audit export"))
                .toList();
    }

    public Map<String, Object> exportAuditData(String username, Long requestId) {
        User user = userService.getUser(username);
        AuditExportRequestEntity request = exportRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Export request not found"));

        if (!request.getUsername().equals(username)) {
            throw new IllegalArgumentException("Export request does not belong to this user");
        }

        expireIfNeeded(request);
        if (!request.isApproved() || request.isExpired()) {
            throw new IllegalArgumentException("Export request is not approved");
        }

        Instant since = Instant.now().minus(request.getLookbackDays(), ChronoUnit.DAYS);
        List<EncryptedMessage> messages = encryptedMessageRepository.findByReceiverAndModeAndCreatedAtAfterOrderByCreatedAtAsc(
                username,
                MessageMode.AUDIT,
                since
        );

        return Map.of(
                "requestId", requestId,
                "organization", user.getOrganization(),
                "messageCount", messages.size(),
                "messages", messages.stream().map(message -> Map.of(
                        "id", message.getId(),
                        "sender", message.getSender(),
                        "receiver", message.getReceiver(),
                        "mode", message.getMode().name(),
                        "encryptedMessage", message.getEncryptedMessage(),
                        "encryptedAESKey", message.getEncryptedAESKey(),
                        "signature", message.getSignature(),
                        "iv", message.getIv(),
                        "createdAt", message.getCreatedAt()
                )).toList()
        );
    }

    private void requireSameOrg(User left, User right) {
        if (!left.getOrganization().equals(right.getOrganization())) {
            throw new IllegalArgumentException("Users are not in the same organization");
        }
    }

    private void expireIfNeeded(AuditExportRequestEntity request) {
        if (!request.isExpired() && request.getExpiresAt().isBefore(Instant.now())) {
            request.setExpired(true);
            request.setApproved(false);
            exportRequestRepository.save(request);
        }
    }

    private AuditExportResponse toResponse(AuditExportRequestEntity request, String detail) {
        String status;
        if (request.isExpired()) {
            status = "EXPIRED";
        } else if (request.isApproved()) {
            status = "APPROVED";
        } else {
            status = "PENDING";
        }
        return new AuditExportResponse(request.getId(), status, detail, request.getExpiresAt());
    }
}
