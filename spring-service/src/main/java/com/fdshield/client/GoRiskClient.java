package com.fdshield.client;

import com.fdshield.entity.FixedDeposit;
import com.fdshield.entity.User;
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
public class GoRiskClient {

    private final RestTemplate restTemplate;
    private final String goRiskUrl;

    public GoRiskClient(RestTemplate restTemplate,
                        @Value("${services.go-risk.url}") String goRiskUrl) {
        this.restTemplate = restTemplate;
        this.goRiskUrl = goRiskUrl;
    }

    public RiskResult analyze(User user, List<FixedDeposit> fds) {
        var payload = Map.of(
                "monthlyIncome", user.getMonthlyIncome(),
                "monthlyExpenses", user.getMonthlyExpenses(),
                "fds", fds.stream().map(fd -> Map.of(
                        "id", fd.getId(),
                        "principal", fd.getPrincipal(),
                        "interestRate", fd.getInterestRate(),
                        "durationMonths", fd.getDurationMonths(),
                        "maturityDate", fd.getMaturityDate().toString(),
                        "fdType", fd.getFdType()
                )).toList()
        );

        log.info("Calling Go Risk Engine at {}/risk/analyze", goRiskUrl);
        var response = restTemplate.postForObject(goRiskUrl + "/risk/analyze", payload, RiskResult.class);
        return response != null ? response : new RiskResult();
    }

    @Getter @Setter @NoArgsConstructor
    public static class RiskResult {
        public BigDecimal liquidityScore = BigDecimal.ZERO;
        public BigDecimal maturitySpreadScore = BigDecimal.ZERO;
        public BigDecimal penaltyExposure = BigDecimal.ZERO;
        public BigDecimal concentrationRisk = BigDecimal.ZERO;
        public BigDecimal ladderScore = BigDecimal.ZERO;
    }
}