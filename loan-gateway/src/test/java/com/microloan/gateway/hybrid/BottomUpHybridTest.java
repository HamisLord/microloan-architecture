package com.microloan.gateway.hybrid;

import com.microloan.gateway.dto.LoanCreatedEvent;
import com.microloan.gateway.dto.LoanRequestDto;
import com.microloan.gateway.dto.ScoringResult;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.entity.LoanStatus;
import com.microloan.gateway.repository.LoanApplicationRepository;
import com.microloan.gateway.service.LoanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(LoanService.class)
class BottomUpHybridTest {

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @MockitoBean
    private KafkaTemplate<String, LoanCreatedEvent> kafkaTemplate;

    @Test
    @DisplayName("Тест 5 (Позитивный): Сервис сохраняет заявку в реальную базу данных с генерацией UUID")
    void test5_createLoan_persistsInRealDb() {
        LoanRequestDto dto = LoanRequestDto.builder()
                .name("Сергей Ветров")
                .passport("9999888877")
                .email("sergey@vetrov.ru")
                .loanAmount(new BigDecimal("120000.00"))
                .monthsTime(24)
                .build();

        LoanApplication created = loanService.createLoan(dto);

        assertThat(created.getId()).isNotNull();
        Optional<LoanApplication> fromDb = loanApplicationRepository.findById(created.getId());
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getStatus()).isEqualTo(LoanStatus.CREATED);
    }

    @Test
    @DisplayName("Тест 6 (Позитивный): Сервис обновляет статус на APPROVED при успешном скоринге в БД")
    void test6_updateLoanStatus_approved_updatesDbRecord() {
        LoanApplication initial = loanApplicationRepository.save(LoanApplication.builder()
                .name("Анна Каренина")
                .passport("1111222233")
                .email("anna@test.ru")
                .loanAmount(new BigDecimal("50000.00"))
                .monthsTime(6)
                .status(LoanStatus.CREATED)
                .build());

        ScoringResult result = new ScoringResult(initial.getId().toString(), true, null);
        loanService.updateLoanStatus(result);

        LoanApplication updated = loanApplicationRepository.findById(initial.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(LoanStatus.APPROVED);
    }

    @Test
    @DisplayName("Тест 7 (Негативный): Сервис обновляет статус на REJECTED при отказе скоринга в БД")
    void test7_updateLoanStatus_rejected_updatesDbRecord() {
        LoanApplication initial = loanApplicationRepository.save(LoanApplication.builder()
                .name("Петр Сидоров")
                .passport("5555666677")
                .email("petr@test.ru")
                .loanAmount(new BigDecimal("300000.00"))
                .monthsTime(36)
                .status(LoanStatus.CREATED)
                .build());

        ScoringResult result = new ScoringResult(initial.getId().toString(), false, null);
        loanService.updateLoanStatus(result);

        LoanApplication updated = loanApplicationRepository.findById(initial.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(LoanStatus.REJECTED);
    }

    @Test
    @DisplayName("Тест 8 (Негативный): Скоринг для несуществующего UUID не ломает БД и не создает записей")
    void test8_updateLoanStatus_nonExistingId_doesNotAffectDb() {
        long countBefore = loanApplicationRepository.count();
        ScoringResult result = new ScoringResult(UUID.randomUUID().toString(), true, "Плохая кредитная история");

        loanService.updateLoanStatus(result);

        assertThat(loanApplicationRepository.count()).isEqualTo(countBefore);
    }
}