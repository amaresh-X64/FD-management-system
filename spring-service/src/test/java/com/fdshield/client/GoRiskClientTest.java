package com.fdshield.client;

import com.fdshield.entity.FixedDeposit;
import com.fdshield.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoRiskClientTest {

    @Mock private RestTemplate restTemplate;
    private GoRiskClient client;
    private User testUser;

    @BeforeEach
    void setUp() {
        client = new GoRiskClient(restTemplate, "http://localhost:8081");
        testUser = User.builder()
                .id(1L).name("Arjun").email("arjun@example.com")
                .monthlyIncome(new BigDecimal("80000"))
                .monthlyExpenses(new BigDecimal("40000"))
                .build();
    }

    static Stream<Arguments> analyzeRiskCases() {
        return Stream.of(
                Arguments.of("Null response → default zero scores", null, 0.0),
                Arguments.of("Valid response → liquidity score mapped", buildResult(75.0), 75.0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("analyzeRiskCases")
    @DisplayName("analyze – maps Go response to RiskResult")
    void analyze_mapsResponse(String desc, GoRiskClient.RiskResult stub, double expectedLiquidity) {
        when(restTemplate.postForObject(anyString(), any(), eq(GoRiskClient.RiskResult.class)))
                .thenReturn(stub);
        GoRiskClient.RiskResult result = client.analyze(testUser, List.of(buildFd("SHORT_TERM")));
        assertThat(result.liquidityScore.doubleValue()).isEqualTo(expectedLiquidity);
    }

    @Test
    @DisplayName("analyze – calls /risk/analyze endpoint")
    void analyze_callsCorrectUrl() {
        when(restTemplate.postForObject(anyString(), any(), eq(GoRiskClient.RiskResult.class)))
                .thenReturn(new GoRiskClient.RiskResult());
        client.analyze(testUser, List.of(buildFd("LONG_TERM")));
        verify(restTemplate).postForObject(
                eq("http://localhost:8081/risk/analyze"), any(), eq(GoRiskClient.RiskResult.class));
    }

    @Test
    @DisplayName("analyze – works with empty FD list")
    void analyze_emptyFds() {
        when(restTemplate.postForObject(anyString(), any(), eq(GoRiskClient.RiskResult.class)))
                .thenReturn(new GoRiskClient.RiskResult());
        GoRiskClient.RiskResult result = client.analyze(testUser, List.of());
        assertThat(result).isNotNull();
    }

    private static GoRiskClient.RiskResult buildResult(double liq) {
        GoRiskClient.RiskResult r = new GoRiskClient.RiskResult();
        r.liquidityScore      = BigDecimal.valueOf(liq);
        r.maturitySpreadScore = BigDecimal.ZERO;
        r.penaltyExposure     = BigDecimal.ZERO;
        r.concentrationRisk   = BigDecimal.ZERO;
        r.ladderScore         = BigDecimal.ZERO;
        return r;
    }

    private static FixedDeposit buildFd(String type) {
        return FixedDeposit.builder()
                .id(1L).principal(new BigDecimal("200000"))
                .interestRate(new BigDecimal("7.5")).durationMonths(12)
                .startDate(LocalDate.of(2025, 1, 1))
                .maturityDate(LocalDate.of(2026, 1, 1))
                .fdType(type).status("ACTIVE").build();
    }
}