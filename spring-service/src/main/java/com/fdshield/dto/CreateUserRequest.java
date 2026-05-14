package com.fdshield.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateUserRequest {
    @NotBlank public String name;
    @Email @NotBlank public String email;
    @NotNull @Positive public BigDecimal monthlyIncome;
    @NotNull @Positive public BigDecimal monthlyExpenses;
}
