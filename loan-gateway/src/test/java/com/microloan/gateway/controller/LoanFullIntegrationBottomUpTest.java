package com.microloan.gateway.controller;

import com.microloan.gateway.dto.LoanCreatedEvent;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.repository.LoanApplicationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LoanFullIntegrationBottomUpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @MockitoBean
    private KafkaTemplate<String, LoanCreatedEvent> kafkaTemplate;

    @Test
    @DisplayName("Тест 5 (Верх): HTTP-запрос через контроллер проходит через сервис и сохраняется в БД")
    void step5_fullEndToEndFlow_controllerToDatabase() throws Exception {
        mockMvc.perform(post("/api/v1/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "name": "Константин Морозов",
                      "passport": "4600777666",
                      "email": "konst@example.com",
                      "loanAmount": 150000.00,
                      "monthsTime": 18
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.name").value("Константин Морозов"));

        List<LoanApplication> all = loanApplicationRepository.findAll();
        assertThat(all).anyMatch(app ->
                app.getName().equals("Константин Морозов") &&
                        app.getMonthsTime().equals(18)
        );
    }
}