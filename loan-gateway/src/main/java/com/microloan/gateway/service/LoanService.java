package com.microloan.gateway.service;

import com.microloan.gateway.dto.LoanCreatedEvent;
import com.microloan.gateway.dto.LoanRequestDto;
import com.microloan.gateway.dto.ScoringResult;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.entity.LoanStatus;
import com.microloan.gateway.repository.LoanApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanApplicationRepository loanApplicationRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public LoanApplication createLoan(LoanRequestDto dto) {
        log.info("Регистрация заявки в ДБ для клиента: {}", dto.getName());

        LoanApplication application = LoanApplication.builder()
                .name(dto.getName())
                .passport(dto.getPassport())
                .email(dto.getEmail())
                .loanAmount(dto.getLoanAmount())
                .monthsTime(dto.getMonthsTime())
                .status(LoanStatus.CREATED)
                .build();

        LoanApplication savedApplication = loanApplicationRepository.save(application);
        log.info("заявка сохранена в БД с ID: {}", savedApplication.getId());

        LoanCreatedEvent event = new LoanCreatedEvent(
                savedApplication.getId().toString(),
                savedApplication.getName(),
                savedApplication.getPassport(),
                savedApplication.getLoanAmount(),
                savedApplication.getMonthsTime()
        );

        // в kafka событие уйдёт после коммита транзакции, см. LoanEventPublisher
        eventPublisher.publishEvent(event);
        return savedApplication;

    }

    @Transactional(readOnly = true)
    public Optional<LoanApplication> getLoanById(UUID id) {
        return loanApplicationRepository.findById(id);
    }

    @Transactional
    public void updateLoanStatus(ScoringResult result) {
        log.info("Получен ответ от скоринга для заявки: {}", result.getId());

        UUID loanId = UUID.fromString(result.getId());

        loanApplicationRepository.findById(loanId).ifPresentOrElse(loan -> {
            if (result.isApproved()) {
                loan.setStatus(LoanStatus.APPROVED);
            } else {
                loan.setStatus(LoanStatus.REJECTED);
            }

            loanApplicationRepository.save(loan);
            log.info("Статус заявки {} успешно обновлен на: {}", loanId, loan.getStatus());
        }, () -> log.warn("Заявка {} не найдена, результат скоринга пропущен", loanId));
    }
}
