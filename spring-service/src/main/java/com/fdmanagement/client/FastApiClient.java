package com.fdmanagement.client;

import com.fdmanagement.entity.FixedDeposit;
import com.fdmanagement.entity.User;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class FastApiClient {

    private final RestTemplate restTemplate;
    private final String fastapiUrl;

    public FastApiClient(RestTemplate restTemplate,
            @Value("${services.fastapi.url}") String fastapiUrl) {
        this.restTemplate = restTemplate;
        this.fastapiUrl = fastapiUrl;
    }

    public AnalyticsResult generate(User user, List<FixedDeposit> fds, GoRiskClient.RiskResult risk) {
        var payload = Map.of(
                "userId", user.getId(),
                "monthlyIncome", user.getMonthlyIncome(),
                "monthlyExpenses", user.getMonthlyExpenses(),
                "liquidityScore", risk.liquidityScore,
                "maturitySpreadScore", risk.maturitySpreadScore,
                "penaltyExposure", risk.penaltyExposure,
                "concentrationRisk", risk.concentrationRisk,
                "ladderScore", risk.ladderScore,
                "fds", fds.stream().map(fd -> Map.of(
                        "id", fd.getId(),
                        "principal", fd.getPrincipal(),
                        "maturityAmount", fd.getMaturityAmount(),
                        "durationMonths", fd.getDurationMonths(),
                        "maturityDate", fd.getMaturityDate().toString(),
                        "fdType", fd.getFdType())).toList());

        log.info("Calling FastAPI Analytics at {}/analytics/generate", fastapiUrl);
        var response = restTemplate.postForObject(fastapiUrl + "/analytics/generate", payload, AnalyticsResult.class);
        return response != null ? response : new AnalyticsResult();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class AnalyticsResult {
        public String persona = "Unknown";
        public BigDecimal portfolioHealthScore = BigDecimal.ZERO;
        public String recommendation = "No recommendations available.";
    }
}