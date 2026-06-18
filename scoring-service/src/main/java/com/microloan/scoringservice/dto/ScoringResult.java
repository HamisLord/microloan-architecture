package com.microloan.scoringservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ScoringResult {
    private boolean approved;
    private String rejectReason;
}
