package com.microloan.gateway.hybrid;

import com.microloan.gateway.dto.LoanCreatedEvent;
import com.microloan.gateway.entity.LoanApplication;
import com.microloan.gateway.entity.LoanStatus;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConvergenceHybridTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @MockitoBean
    private KafkaTemplate<String, LoanCreatedEvent> kafkaTemplate;

    @Test
    @DisplayName("Тест 9 (Схождение, Позитивный): Полный сквозной цикл от HTTP POST до реальной записи в БД")
    void test9_fullConvergence_postRequestPersistsInDatabase() throws Exception {
        mockMvc.perform(post("/api/v1/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "name": "Евгений Онегин",
                      "passport": "1029384756",
                      "email": "onegin@test.com",
                      "loanAmount": 250000.00,
                      "monthsTime": 36
                    }
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("CREATED"));

        assertThat(loanApplicationRepository.findAll())
                .anyMatch(app -> app.getName().equals("Евгений Онегин") && app.getStatus() == LoanStatus.CREATED);
    }

    @Test
    @DisplayName("Тест 10 (Схождение, Негативный): Сквозной GET для несуществующего ID возвращает 404 через весь стек")
    void test10_fullConvergence_getNonExistingId_returns404FromRealDb() throws Exception {
        UUID randomId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/loans/" + randomId))
                .andExpect(status().isNotFound());
    }
}