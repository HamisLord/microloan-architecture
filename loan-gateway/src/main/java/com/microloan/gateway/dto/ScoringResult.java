package com.microloan.gateway.dto;

import lombok.Data;

@Data
public class ScoringResult {
    private String id;
    private boolean approved;
    private String rejectReason;
}
