package com.zerotrace.auth_server.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuditApprovalRequest {
    private boolean approved;
}
