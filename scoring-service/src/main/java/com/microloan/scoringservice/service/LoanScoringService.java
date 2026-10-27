package com.microloan.scoringservice.service;

import com.microloan.scoringservice.antifraud.SlidingWindowRateLimiter;
import com.microloan.scoringservice.dto.LoanApplicationEvent;
import com.microloan.scoringservice.dto.ScoringResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanScoringService {
    private static final BigDecimal MAXIMUM_LOAN = new BigDecimal("1000000");
    private static final int MAXIMUM_MONTHS = 60;
    private static final BigDecimal MINIMUM_LOAN = new BigDecimal("2000");

    private final SlidingWindowRateLimiter rateLimiter;

    public ScoringResult processApplication(LoanApplicationEvent event) {
        log.info("Начинается проверка заявки Клиента: {}", event.getName());

        if (event.getPassport() == null || event.getPassport().isBlank()) {
            return new ScoringResult(event.getId(), false, "Не указан паспорт");
        }

        // антифрод проверяется первым: каждая заявка должна попасть в окно, даже если её отклонят по другим правилам
        if (!rateLimiter.tryAcquire(event.getPassport())) {
            return new ScoringResult(event.getId(), false, "Слишком много заявок: не больше "
                    + rateLimiter.getMaxApplications() + " за " + rateLimiter.getWindow().toHours() + " ч");
        }

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
