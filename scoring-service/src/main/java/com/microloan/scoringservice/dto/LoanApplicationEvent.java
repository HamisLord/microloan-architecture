package com.microloan.scoringservice.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanApplicationEvent {
    private String id;
    private String name;
    private String passport;
    private String email;
    private BigDecimal loanAmount;
    private Integer monthsTime;
}
