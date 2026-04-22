package com.zerotrace.auth_server.model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExportRequest {
    private Integer lookbackDays = 7;
}
