package com.microloan.gateway.messaging;

import com.microloan.gateway.dto.LoanCreatedEvent;
import com.microloan.gateway.dto.LoanRequestDto;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.repository.LoanApplicationRepository;
import com.microloan.gateway.service.LoanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({LoanService.class, LoanEventPublisher.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED) // нужны настоящие коммиты, а не откат тестовой транзакции
class LoanEventPublisherTransactionTest {

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private KafkaTemplate<String, LoanCreatedEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        when(kafkaTemplate.send(anyString(), anyString(), any(LoanCreatedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    @AfterEach
    void cleanUp() {
        loanApplicationRepository.deleteAll();
    }

    @Test
    @DisplayName("Событие уходит в Kafka только после коммита транзакции")
    void eventIsSentOnlyAfterCommit() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AtomicReference<LoanApplication> created = new AtomicReference<>();

        transaction.executeWithoutResult(status -> {
            created.set(loanService.createLoan(validRequest()));
            // транзакция ещё открыта — скоринг не должен узнать о заявке
            verify(kafkaTemplate, never()).send(anyString(), anyString(), any(LoanCreatedEvent.class));
        });

        String id = created.get().getId().toString();
        verify(kafkaTemplate).send(eq(LoanEventPublisher.NEW_APPLICATIONS_TOPIC), eq(id), any(LoanCreatedEvent.class));
    }

    @Test
    @DisplayName("При откате транзакции событие в Kafka не отправляется")
    void eventIsNotSentOnRollback() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            loanService.createLoan(validRequest());
            status.setRollbackOnly();
        });

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(LoanCreatedEvent.class));
        assertThat(loanApplicationRepository.count()).isZero();
    }

    @Test
    @DisplayName("Ошибка отправки в Kafka не откатывает уже сохранённую заявку")
    void kafkaFailureDoesNotBreakCreatedLoan() {
        when(kafkaTemplate.send(anyString(), anyString(), any(LoanCreatedEvent.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker недоступен")));

        LoanApplication created = loanService.createLoan(validRequest());

        assertThat(loanApplicationRepository.findById(created.getId())).isPresent();
    }

    private LoanRequestDto validRequest() {
        return LoanRequestDto.builder()
                .name("Ирина Белова")
                .passport("1234567890")
                .email("irina@test.ru")
                .loanAmount(new BigDecimal("80000.00"))
                .monthsTime(12)
                .build();
    }
}
