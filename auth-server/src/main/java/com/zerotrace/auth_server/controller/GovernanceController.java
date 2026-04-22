package com.zerotrace.auth_server.controller;

import com.zerotrace.auth_server.model.*;
import com.zerotrace.auth_server.service.GovernanceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/governance")
@CrossOrigin(origins = "*")
public class GovernanceController {

    private final GovernanceService governanceService;

    public GovernanceController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    @PutMapping("/audit-approval/{username}")
    public Map<String, String> setAuditApproval(
            Authentication authentication,
            @PathVariable String username,
            @RequestBody AuditApprovalRequest request
    ) {
        governanceService.setAuditApproval(authentication.getName(), username, request.isApproved());
        return Map.of("status", "updated");
    }

    @PostMapping("/export-requests")
    public AuditExportResponse createExportRequest(Authentication authentication, @RequestBody ExportRequest request) {
        return governanceService.createExportRequest(authentication.getName(), request.getLookbackDays());
    }

    @GetMapping("/export-requests/pending")
    public List<AuditExportResponse> pendingExportRequests(Authentication authentication) {
        return governanceService.pendingExportRequests(authentication.getName());
    }

    @GetMapping("/export-requests/mine")
    public List<AuditExportResponse> myExportRequests(Authentication authentication) {
        return governanceService.exportRequestsForUser(authentication.getName());
    }

    @PutMapping("/export-requests/{requestId}")
    public Map<String, String> approveExportRequest(
            Authentication authentication,
            @PathVariable Long requestId,
            @RequestBody ExportApprovalRequest request
    ) {
        governanceService.approveExportRequest(authentication.getName(), requestId, request.isApproved());
        return Map.of("status", "updated");
    }

    @GetMapping("/export/{requestId}")
    public Map<String, Object> export(Authentication authentication, @PathVariable Long requestId) {
        return governanceService.exportAuditData(authentication.getName(), requestId);
    }
}
