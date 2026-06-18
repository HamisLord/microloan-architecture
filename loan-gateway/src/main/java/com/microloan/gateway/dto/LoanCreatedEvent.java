package com.microloan.gateway.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanCreatedEvent {
    private String id;
    private String name;
    private String passport;
    private BigDecimal loanAmount;
    private Integer monthsTime;
}
