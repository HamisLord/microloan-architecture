package com.microloan.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScoringResult {
    private String id;
    private boolean approved;
    private String rejectReason;
}
