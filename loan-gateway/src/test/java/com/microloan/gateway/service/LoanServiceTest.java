package com.microloan.gateway.service;

import com.microloan.gateway.dto.LoanCreatedEvent;
import com.microloan.gateway.dto.LoanRequestDto;
import com.microloan.gateway.dto.ScoringResult;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.entity.LoanStatus;
import com.microloan.gateway.repository.LoanApplicationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private LoanService loanService;

    @Test
    @DisplayName("1. createLoan: должен сохранить заявку со статусом CREATED и опубликовать событие для Kafka")
    void createLoan_shouldSaveEntityAndSendKafkaEvent() {
        UUID generatedId = UUID.randomUUID();
        LoanRequestDto dto = LoanRequestDto.builder()
                .name("Иван Иванов")
                .passport("1234567890")
                .email("ivan@example.com")
                .loanAmount(new BigDecimal("50000.00"))
                .monthsTime(12)
                .build();

        when(loanApplicationRepository.save(any(LoanApplication.class)))
                .thenAnswer(invocation -> {
                    LoanApplication app = invocation.getArgument(0);
                    app.setId(generatedId);
                    return app;
                });

        LoanApplication result = loanService.createLoan(dto);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(generatedId);
        assertThat(result.getStatus()).isEqualTo(LoanStatus.CREATED);
        assertThat(result.getName()).isEqualTo("Иван Иванов");

        ArgumentCaptor<LoanApplication> appCaptor = ArgumentCaptor.forClass(LoanApplication.class);
        verify(loanApplicationRepository).save(appCaptor.capture());
        assertThat(appCaptor.getValue().getStatus()).isEqualTo(LoanStatus.CREATED);

        ArgumentCaptor<LoanCreatedEvent> eventCaptor = ArgumentCaptor.forClass(LoanCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        LoanCreatedEvent sentEvent = eventCaptor.getValue();
        assertThat(sentEvent.getId()).isEqualTo(generatedId.toString());
        assertThat(sentEvent.getName()).isEqualTo("Иван Иванов");
        assertThat(sentEvent.getLoanAmount()).isEqualTo(new BigDecimal("50000.00"));
        assertThat(sentEvent.getMonthsTime()).isEqualTo(12);
    }

    @Test
    @DisplayName("2. getLoanById: должен вернуть заявку, если она найдена")
    void getLoanById_whenFound_shouldReturnLoan() {
        UUID id = UUID.randomUUID();
        LoanApplication application = LoanApplication.builder()
                .id(id)
                .name("Петр Петров")
                .status(LoanStatus.CREATED)
                .build();

        when(loanApplicationRepository.findById(id)).thenReturn(Optional.of(application));

        Optional<LoanApplication> result = loanService.getLoanById(id);

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getName()).isEqualTo("Петр Петров");
        verify(loanApplicationRepository, times(1)).findById(id);
    }

    @Test
    @DisplayName("3. updateLoanStatus: должен установить APPROVED, если скоринг успешен")
    void updateLoanStatus_whenApproved_shouldUpdateStatusToApproved() {
        UUID id = UUID.randomUUID();
        LoanApplication existingApp = LoanApplication.builder()
                .id(id)
                .name("Анна Смирнова")
                .status(LoanStatus.CREATED)
                .build();

        ScoringResult scoringResult = new ScoringResult(id.toString(), true, null);

        when(loanApplicationRepository.findById(id)).thenReturn(Optional.of(existingApp));

        loanService.updateLoanStatus(scoringResult);

        assertThat(existingApp.getStatus()).isEqualTo(LoanStatus.APPROVED);
        verify(loanApplicationRepository).save(existingApp);
    }

    @Test
    @DisplayName("4. updateLoanStatus: должен установить REJECTED, если скоринг отклонен")
    void updateLoanStatus_whenRejected_shouldUpdateStatusToRejected() {
        UUID id = UUID.randomUUID();
        LoanApplication existingApp = LoanApplication.builder()
                .id(id)
                .name("Сергей Васильев")
                .status(LoanStatus.CREATED)
                .build();

        ScoringResult scoringResult = new ScoringResult(id.toString(), false, "Отказ скоринга");

        when(loanApplicationRepository.findById(id)).thenReturn(Optional.of(existingApp));

        loanService.updateLoanStatus(scoringResult);

        assertThat(existingApp.getStatus()).isEqualTo(LoanStatus.REJECTED);
        verify(loanApplicationRepository).save(existingApp);
    }

    @Test
    @DisplayName("5. updateLoanStatus: если заявка не найдена в БД, метод не должен вызывать save")
    void updateLoanStatus_whenLoanNotFound_shouldDoNothing() {
        UUID nonExistentId = UUID.randomUUID();
        ScoringResult scoringResult = new ScoringResult(nonExistentId.toString(), true, null);

        when(loanApplicationRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        loanService.updateLoanStatus(scoringResult);


        verify(loanApplicationRepository, times(1)).findById(nonExistentId);
        verify(loanApplicationRepository, never()).save(any(LoanApplication.class));
    }
}