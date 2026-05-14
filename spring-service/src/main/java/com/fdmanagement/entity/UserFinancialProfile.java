package com.fdmanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_financial_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFinancialProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    private String persona;

    @Column(name = "liquidity_score", precision = 5, scale = 2)
    private BigDecimal liquidityScore;

    @Column(name = "maturity_spread_score", precision = 5, scale = 2)
    private BigDecimal maturitySpreadScore;

    @Column(name = "penalty_risk", precision = 5, scale = 2)
    private BigDecimal penaltyRisk;

    @Column(name = "concentration_risk", precision = 5, scale = 2)
    private BigDecimal concentrationRisk;

    @Column(name = "ladder_score", precision = 5, scale = 2)
    private BigDecimal ladderScore;

    @Column(name = "portfolio_health_score", precision = 5, scale = 2)
    private BigDecimal portfolioHealthScore;

    @Column(columnDefinition = "TEXT")
    private String recommendation;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}