package com.fdshield.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FdResponse {
    public Long id;
    public Long userId;
    public BigDecimal principal;
    public BigDecimal interestRate;
    public Integer durationMonths;
    public BigDecimal maturityAmount;
    public LocalDate startDate;
    public LocalDate maturityDate;
    public String fdType;
    public String status;
}
