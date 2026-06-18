package com.microloan.gateway.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRequestDto {

    @NotBlank(message = "имя не должно быть пустым")
    @Size(min = 2, max = 50, message = "имя должно быть от 2 до 50 символов")
    private String name;

    @NotBlank(message = "номер паспорта обязателен")
    @Pattern(regexp = "^\\d{10}$", message = "должно быть ровно 10 цифр")
    private String passport;

    @NotBlank(message = "email обязателен")
    @Email(message = "неверный формат email")
    private String email;

    @NotNull(message = "сумма кредита обязательна")
    @DecimalMin(value = "1000.00", message = "минимальная сумма кредита 1 000₽ ")
    @DecimalMax(value = "10000000.00", message = "Максимальная сумма кредита 10 000 000₽")
    private BigDecimal loanAmount;

    @NotNull(message = "срок кредита обязателен")
    @Min(value = 1, message = "Минимальный срок 1 месяц")
    @Max(value = 120, message = "Максимальный срок 10 лет")
    private Integer monthsTime;
}
