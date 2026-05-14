package com.fdmanagement.client;
import com.fdmanagement.entity.FixedDeposit;
import com.fdmanagement.entity.User;
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
class FastApiClientTest {

    @Mock
    private RestTemplate restTemplate;
    private FastApiClient client;
    private User testUser;
    private GoRiskClient.RiskResult riskResult;

    @BeforeEach
    void setUp() {
        client = new FastApiClient(restTemplate, "http://localhost:8082");
        testUser = User.builder()
                .id(1L).name("Arjun").email("arjun@example.com")
                .monthlyIncome(new BigDecimal("80000"))
                .monthlyExpenses(new BigDecimal("40000"))
                .build();
        riskResult = new GoRiskClient.RiskResult();
        riskResult.liquidityScore = BigDecimal.valueOf(70);
        riskResult.maturitySpreadScore = BigDecimal.valueOf(60);
        riskResult.penaltyExposure = BigDecimal.valueOf(20);
        riskResult.concentrationRisk = BigDecimal.valueOf(65);
        riskResult.ladderScore = BigDecimal.valueOf(70);
    }

    static Stream<Arguments> generateCases() {
        return Stream.of(
                Arguments.of("Null response → default unknown persona", null, "Unknown"),
                Arguments.of("Valid response → persona mapped", buildResult("Conservative Saver", 85.0),
                        "Conservative Saver"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("generateCases")
    @DisplayName("generate – maps FastAPI response to AnalyticsResult")
    void generate_mapsResponse(String desc, FastApiClient.AnalyticsResult stub, String expectedPersona) {
        when(restTemplate.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class)))
                .thenReturn(stub);
        FastApiClient.AnalyticsResult result = client.generate(testUser, List.of(buildFd()), riskResult);
        assertThat(result.persona).isEqualTo(expectedPersona);
    }

    @Test
    @DisplayName("generate – calls /analytics/generate endpoint")
    void generate_callsCorrectUrl() {
        when(restTemplate.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class)))
                .thenReturn(new FastApiClient.AnalyticsResult());
        client.generate(testUser, List.of(buildFd()), riskResult);
        verify(restTemplate).postForObject(
                eq("http://localhost:8082/analytics/generate"), any(), eq(FastApiClient.AnalyticsResult.class));
    }

    @Test
    @DisplayName("generate – works with empty FD list")
    void generate_emptyFds() {
        when(restTemplate.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class)))
                .thenReturn(new FastApiClient.AnalyticsResult());
        FastApiClient.AnalyticsResult result = client.generate(testUser, List.of(), riskResult);
        assertThat(result).isNotNull();
    }

    private static FastApiClient.AnalyticsResult buildResult(String persona, double health) {
        FastApiClient.AnalyticsResult r = new FastApiClient.AnalyticsResult();
        r.persona = persona;
        r.portfolioHealthScore = BigDecimal.valueOf(health);
        r.recommendation = "Keep it up";
        return r;
    }

    private static FixedDeposit buildFd() {
        return FixedDeposit.builder()
                .id(1L)
                .principal(new BigDecimal("200000"))
                .maturityAmount(new BigDecimal("230000"))
                .interestRate(new BigDecimal("7.5"))
                .durationMonths(24)
                .startDate(LocalDate.of(2025, 1, 1))
                .maturityDate(LocalDate.of(2027, 1, 1))
                .fdType("LONG_TERM")
                .status("ACTIVE")
                .build();
    }
}