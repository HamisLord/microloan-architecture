package com.microloan.gateway.repository;

import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.entity.LoanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LoanRepositoryBottomUpTest {

    @Autowired
    private LoanApplicationRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Тест 1 (Низ): Сохранение сущности в БД и генерация идентификатора")
    void step1_saveApplication_persistsInDatabase() {
        LoanApplication application = LoanApplication.builder()
                .name("Алексей Смирнов")
                .passport("4500 123456")
                .email("alex@example.com")
                .loanAmount(new BigDecimal("75000"))
                .monthsTime(6)
                .status(LoanStatus.CREATED)
                .build();

        LoanApplication saved = repository.save(application);
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();
        LoanApplication fromDb = entityManager.find(LoanApplication.class, saved.getId());
        assertThat(fromDb).isNotNull();
        assertThat(fromDb.getName()).isEqualTo("Алексей Смирнов");
        assertThat(fromDb.getStatus()).isEqualTo(LoanStatus.CREATED);
    }

    @Test
    @DisplayName("Тест 2 (Низ): Поиск по ID и обновление статуса в транзакции БД")
    void step2_findAndPersistStatusChange_updatesRecordInDatabase() {
        LoanApplication application = LoanApplication.builder()
                .name("Ольга Попова")
                .passport("4510 654321")
                .email("olga@example.com")
                .loanAmount(new BigDecimal("30000"))
                .monthsTime(3)
                .status(LoanStatus.CREATED)
                .build();

        LoanApplication persisted = entityManager.persistAndFlush(application);
        UUID id = persisted.getId();
        entityManager.clear();

        Optional<LoanApplication> foundOpt = repository.findById(id);
        assertThat(foundOpt).isPresent();

        LoanApplication toUpdate = foundOpt.get();
        toUpdate.setStatus(LoanStatus.APPROVED);
        repository.save(toUpdate);
        entityManager.flush();
        entityManager.clear();

        LoanApplication reloaded = entityManager.find(LoanApplication.class, id);
        assertThat(reloaded.getStatus()).isEqualTo(LoanStatus.APPROVED);
    }
}