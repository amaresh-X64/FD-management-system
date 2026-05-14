package com.fdshield.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "withdrawal_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WithdrawalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fd_id", nullable = false)
    private FixedDeposit fixedDeposit;

    // PREMATURE or MATURITY
    @Column(name = "withdrawal_type", nullable = false)
    private String withdrawalType;

    @Column(name = "penalty_amount", precision = 15, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
