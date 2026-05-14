package com.fdmanagement.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    public Long id;
    public String name;
    public String email;
    public BigDecimal monthlyIncome;
    public BigDecimal monthlyExpenses;
    public LocalDateTime createdAt;
}
