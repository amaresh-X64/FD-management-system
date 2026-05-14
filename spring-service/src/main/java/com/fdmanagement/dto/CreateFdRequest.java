package com.fdmanagement.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFdRequest {
    @NotNull
    public Long userId;
    @NotNull
    @Positive
    public BigDecimal principal;
    @NotNull
    @DecimalMin("0.1")
    @DecimalMax("20.0")
    public BigDecimal interestRate;
    @NotNull
    @Min(1)
    public Integer durationMonths;
    @NotNull
    public LocalDate startDate;
}
