package com.microloan.gateway.exception;

import com.microloan.gateway.controller.LoanController;
import com.microloan.gateway.service.LoanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoanController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoanService loanService;

    @Test
    @DisplayName("id не в формате UUID — 400, а не 500")
    void invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/loans/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Битый JSON в теле запроса — 400, а не 500")
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Иван\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("Непредвиденная ошибка — 500 без деталей исключения в ответе")
    void unexpectedError_returns500WithoutInternalDetails() throws Exception {
        when(loanService.getLoanById(any(UUID.class)))
                .thenThrow(new RuntimeException("SELECT * FROM loan_applications: connection refused"));

        mockMvc.perform(get("/api/v1/loans/" + UUID.randomUUID()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Внутренняя ошибка сервера"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(content().string(not(containsString("loan_applications"))));
    }

    @Test
    @DisplayName("Неизвестный путь — 404, а не 500")
    void unknownPath_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Неподдерживаемый HTTP-метод — 405, а не 500")
    void unsupportedMethod_returns405() throws Exception {
        mockMvc.perform(delete("/api/v1/loans/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed());
    }
}
