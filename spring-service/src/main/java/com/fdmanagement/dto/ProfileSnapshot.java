package com.fdmanagement.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileSnapshot {
    public String persona;
    public BigDecimal liquidityScore;
    public BigDecimal maturitySpreadScore;
    public BigDecimal penaltyRisk;
    public BigDecimal concentrationRisk;
    public BigDecimal ladderScore;
    public BigDecimal portfolioHealthScore;
    public String recommendation;
    public LocalDateTime updatedAt;
}