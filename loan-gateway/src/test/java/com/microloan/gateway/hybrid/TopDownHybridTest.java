package com.microloan.gateway.hybrid;

import com.microloan.gateway.controller.LoanController;
import com.microloan.gateway.dto.LoanCreatedEvent;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.entity.LoanStatus;
import com.microloan.gateway.repository.LoanApplicationRepository;
import com.microloan.gateway.service.LoanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
@Import(LoanService.class)
class TopDownHybridTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanApplicationRepository loanApplicationRepository; // Репозиторий замокан

    @MockitoBean
    private KafkaTemplate<String, LoanCreatedEvent> kafkaTemplate;

    @Test
    @DisplayName("Тест 1 (Позитивный): Создание заявки — валидация DTO и ответ 201 Created")
    void test1_createLoan_success() throws Exception {
        when(loanApplicationRepository.save(any(LoanApplication.class)))
                .thenAnswer(inv -> {
                    LoanApplication app = inv.getArgument(0);
                    app.setId(UUID.randomUUID());
                    return app;
                });

        mockMvc.perform(post("/api/v1/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "name": "Иван Тестов",
                      "passport": "1234567890",
                      "email": "ivan@test.com",
                      "loanAmount": 50000.00,
                      "monthsTime": 12
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @DisplayName("Тест 2 (Негативный): Невалидный паспорт — Bean Validation возвращает 400 Bad Request")
    void test2_createLoan_invalidPassport_returns400() throws Exception {
        // Паспорт содержит пробел и нарушает @Pattern("^\\d{10}$")
        mockMvc.perform(post("/api/v1/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "name": "Иван Тестов",
                      "passport": "1234 567890",
                      "email": "ivan@test.com",
                      "loanAmount": 50000.00,
                      "monthsTime": 12
                    }
                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Тест 3 (Позитивный): Получение существующей заявки по ID возвращает 200 OK")
    void test3_getLoanById_found_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        LoanApplication app = LoanApplication.builder()
                .id(id)
                .name("Ольга Иванова")
                .status(LoanStatus.APPROVED)
                .build();

        when(loanApplicationRepository.findById(id)).thenReturn(Optional.of(app));

        mockMvc.perform(get("/api/v1/loans/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ольга Иванова"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("Тест 4 (Негативный): Запрос несуществующей заявки возвращает 404 Not Found")
    void test4_getLoanById_notFound_returns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(loanApplicationRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/loans/" + nonExistentId))
                .andExpect(status().isNotFound());
    }
}