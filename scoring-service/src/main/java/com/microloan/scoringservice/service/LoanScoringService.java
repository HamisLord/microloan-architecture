package com.microloan.scoringservice.service;

import com.microloan.scoringservice.dto.LoanApplicationEvent;
import com.microloan.scoringservice.dto.ScoringResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
public class LoanScoringService {
    private static final BigDecimal MAXIMUM_LOAN = new BigDecimal("1000000");
    private static final int MAXIMUM_MONTHS = 60;
    private static final BigDecimal MINIMUM_LOAN = new BigDecimal("2000");

    public ScoringResult processApplication(LoanApplicationEvent event) {
        log.info("Начинается проверка заявки Клиента: {}", event.getName());

        if (event.getLoanAmount().compareTo(MAXIMUM_LOAN) > 0) {
            return new ScoringResult(event.getId(), false, "Сумма превышает лимиты банка: 1 000 000");
        }

        if (event.getMonthsTime() > MAXIMUM_MONTHS) {
            return new ScoringResult(event.getId(), false, "срок кредита превышает допустимые 60 месяцев");
        }

        BigDecimal monthlyPayment = event.getLoanAmount().divide(BigDecimal.valueOf(event.getMonthsTime()), 2, RoundingMode.HALF_UP);

        if (monthlyPayment.compareTo(MINIMUM_LOAN) < 0) {
            return new ScoringResult(event.getId(), false, "Ежемесячный платеж слишком маленький: " + monthlyPayment);
        }

        return new ScoringResult(event.getId(), true, "Идеальный клиент");
    }
}
