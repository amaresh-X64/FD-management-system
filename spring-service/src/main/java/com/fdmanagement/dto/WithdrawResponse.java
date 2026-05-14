package com.fdmanagement.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawResponse {
    public Long fdId;
    public Long userId; // used internally to trigger analytics refresh
    public BigDecimal principal;
    public BigDecimal interestEarned;
    public BigDecimal penaltyAmount;
    public BigDecimal netPayout;
    public String message;
}