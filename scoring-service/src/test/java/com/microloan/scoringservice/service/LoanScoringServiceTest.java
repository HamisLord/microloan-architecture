package com.microloan.scoringservice.service;

import com.microloan.scoringservice.antifraud.MutableClock;
import com.microloan.scoringservice.antifraud.SlidingWindowRateLimiter;
import com.microloan.scoringservice.dto.LoanApplicationEvent;
import com.microloan.scoringservice.dto.ScoringResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoanScoringServiceTest {

    private LoanScoringService scoringService;

    @BeforeEach
    void setUp() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-27T10:00:00Z"));
        scoringService = new LoanScoringService(new SlidingWindowRateLimiter(3, Duration.ofHours(24), clock));
    }

    @Test
    @DisplayName("Нормальная заявка одобряется")
    void approvesRegularApplication() {
        ScoringResult result = scoringService.processApplication(event("1234567890", "120000", 12));

        assertThat(result.isApproved()).isTrue();
        assertThat(result.getId()).isEqualTo("loan-1");
    }

    @Test
    @DisplayName("Сумма больше 1 000 000 — отказ")
    void rejectsTooLargeAmount() {
        ScoringResult result = scoringService.processApplication(event("1234567890", "1000001", 12));

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getRejectReason()).contains("лимиты");
    }

    @Test
    @DisplayName("Срок больше 60 месяцев — отказ")
    void rejectsTooLongTerm() {
        ScoringResult result = scoringService.processApplication(event("1234567890", "500000", 61));

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getRejectReason()).contains("60 месяцев");
    }

    @Test
    @DisplayName("Ежемесячный платёж меньше 2000 — отказ")
    void rejectsTooSmallMonthlyPayment() {
        ScoringResult result = scoringService.processApplication(event("1234567890", "10000", 12));

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getRejectReason()).contains("платеж");
    }

    @Test
    @DisplayName("4-я заявка с того же паспорта за сутки отклоняется антифродом")
    void rejectsByAntifraudLimit() {
        for (int i = 0; i < 3; i++) {
            assertThat(scoringService.processApplication(event("1234567890", "120000", 12)).isApproved()).isTrue();
        }

        ScoringResult result = scoringService.processApplication(event("1234567890", "120000", 12));

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getRejectReason()).startsWith("Слишком много заявок");
    }

    @Test
    @DisplayName("Отклонённые по другим правилам заявки тоже расходуют лимит")
    void rejectedApplicationsCountTowardsLimit() {
        for (int i = 0; i < 3; i++) {
            scoringService.processApplication(event("1234567890", "5000000", 12)); // отказ по сумме
        }

        ScoringResult result = scoringService.processApplication(event("1234567890", "120000", 12));

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getRejectReason()).startsWith("Слишком много заявок");
    }

    @Test
    @DisplayName("Заявка без паспорта отклоняется")
    void rejectsMissingPassport() {
        ScoringResult result = scoringService.processApplication(event(null, "120000", 12));

        assertThat(result.isApproved()).isFalse();
        assertThat(result.getRejectReason()).isEqualTo("Не указан паспорт");
    }

    private LoanApplicationEvent event(String passport, String amount, int months) {
        LoanApplicationEvent event = new LoanApplicationEvent();
        event.setId("loan-1");
        event.setName("Тест Тестов");
        event.setPassport(passport);
        event.setLoanAmount(new BigDecimal(amount));
        event.setMonthsTime(months);
        return event;
    }
}
