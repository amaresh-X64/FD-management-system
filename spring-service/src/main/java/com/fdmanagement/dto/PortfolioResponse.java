package com.fdmanagement.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioResponse {
    public UserResponse user;
    public List<FdResponse> activeFds;
    public BigDecimal totalPrincipal;
    public BigDecimal totalMaturityValue;
    public ProfileSnapshot financialProfile;
}
