package com.microloan.gateway.listener;

import com.microloan.gateway.dto.ScoringResult;
import com.microloan.gateway.service.LoanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoringResultListener {

    private final LoanService loanService;

    @KafkaListener(topics = "scoring-results", groupId = "gateway-group")
    public void consumeScoringResult(ScoringResult result) {
        log.info("Результат скоринга из kafka {}", result);
        loanService.updateLoanStatus(result);
    }
}
