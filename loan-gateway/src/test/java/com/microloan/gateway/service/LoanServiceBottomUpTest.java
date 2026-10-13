package com.microloan.gateway.service;

import com.microloan.gateway.dto.LoanCreatedEvent;
import com.microloan.gateway.dto.LoanRequestDto;
import com.microloan.gateway.dto.ScoringResult;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.entity.LoanStatus;
import com.microloan.gateway.repository.LoanApplicationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class LoanServiceBottomUpTest {

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository; // Реальный репозиторий

    @MockitoBean
    private KafkaTemplate<String, LoanCreatedEvent> kafkaTemplate;

    @Test
    @DisplayName("Тест 3 (Середина): Сервис сохраняет заявку в реальную БД и триггерит Kafka")
    void step3_createLoan_integratesServiceWithRealDatabase() {
        LoanRequestDto dto = new LoanRequestDto();
        dto.setName("Дмитрий Кузнецов");
        dto.setPassport("4000 111222");
        dto.setEmail("dmitry@example.com");
        dto.setLoanAmount(new BigDecimal("100000"));
        dto.setMonthsTime(24);

        LoanApplication created = loanService.createLoan(dto);

        Optional<LoanApplication> fromDb = loanApplicationRepository.findById(created.getId());
        assertThat(fromDb).isPresent();
        assertThat(fromDb.get().getStatus()).isEqualTo(LoanStatus.CREATED);
        assertThat(fromDb.get().getName()).isEqualTo("Дмитрий Кузнецов");

        verify(kafkaTemplate).send(eq("new-applications"), eq(created.getId().toString()), any(LoanCreatedEvent.class));
    }

    @Test
    @DisplayName("Тест 4 (Середина): Обработка скоринга сервисом реально обновляет запись в БД")
    void step4_updateLoanStatus_persistsChangesToRealDatabase() {
        LoanApplication app = LoanApplication.builder()
                .name("Мария Соколова")
                .passport("4000 999888")
                .email("maria@example.com")
                .loanAmount(new BigDecimal("45000"))
                .monthsTime(12)
                .status(LoanStatus.CREATED)
                .build();
        LoanApplication saved = loanApplicationRepository.save(app);

        ScoringResult scoringResult = new ScoringResult(saved.getId().toString(), false, "Отказ скоринга");

        loanService.updateLoanStatus(scoringResult);

        LoanApplication updatedInDb = loanApplicationRepository.findById(saved.getId()).orElseThrow();
        assertThat(updatedInDb.getStatus()).isEqualTo(LoanStatus.REJECTED);
    }
}