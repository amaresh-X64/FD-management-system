package com.fdmanagement.client;

import com.fdmanagement.entity.FixedDeposit;
import com.fdmanagement.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
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
                .monthlyExpenses(new BigDecimal("40000")).build();
    }

    private static FixedDeposit buildFd(String type) {
        return FixedDeposit.builder()
                .id(1L).principal(new BigDecimal("200000"))
                .interestRate(new BigDecimal("7.5")).durationMonths(12)
                .startDate(LocalDate.of(2025, 1, 1))
                .maturityDate(LocalDate.of(2026, 1, 1))
                .fdType(type).status("ACTIVE").build();
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

    // ── analyze ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("analyze")
    class Analyze {

        @Test
        void shouldReturnDefaultZeroScores_whenInputIsNullResponseFromGoService() {
            when(restTemplate.postForObject(
                    eq("http://localhost:8081/risk/analyze"),
                    argThat(body -> body instanceof java.util.Map),
                    eq(GoRiskClient.RiskResult.class)))
                    .thenReturn(null);

            GoRiskClient.RiskResult result = client.analyze(testUser, List.of(buildFd("SHORT_TERM")));

            assertThat(result.liquidityScore).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldReturnMappedLiquidityScore_whenInputIsValidResponseFromGoService() {
            when(restTemplate.postForObject(
                    eq("http://localhost:8081/risk/analyze"),
                    argThat(body -> body instanceof java.util.Map),
                    eq(GoRiskClient.RiskResult.class)))
                    .thenReturn(buildResult(75.0));

            GoRiskClient.RiskResult result = client.analyze(testUser, List.of(buildFd("SHORT_TERM")));

            assertThat(result.liquidityScore.doubleValue()).isEqualTo(75.0);
        }

        @Test
        void shouldCallCorrectEndpoint_whenInputIsValidRequest() {
            when(restTemplate.postForObject(
                    eq("http://localhost:8081/risk/analyze"),
                    argThat(body -> body instanceof java.util.Map),
                    eq(GoRiskClient.RiskResult.class)))
                    .thenReturn(new GoRiskClient.RiskResult());

            client.analyze(testUser, List.of(buildFd("LONG_TERM")));

            verify(restTemplate).postForObject(
                    eq("http://localhost:8081/risk/analyze"),
                    argThat(body -> body instanceof java.util.Map),
                    eq(GoRiskClient.RiskResult.class));
        }

        @Test
        void shouldReturnNonNullResult_whenInputIsEmptyFdList() {
            when(restTemplate.postForObject(
                    eq("http://localhost:8081/risk/analyze"),
                    argThat(body -> body instanceof java.util.Map),
                    eq(GoRiskClient.RiskResult.class)))
                    .thenReturn(new GoRiskClient.RiskResult());

            GoRiskClient.RiskResult result = client.analyze(testUser, List.of());

            assertThat(result).isNotNull();
        }

        @Test
        void shouldPropagateException_whenInputIsRestTemplateThrowingException() {
            when(restTemplate.postForObject(
                    eq("http://localhost:8081/risk/analyze"),
                    argThat(body -> body instanceof java.util.Map),
                    eq(GoRiskClient.RiskResult.class)))
                    .thenThrow(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> client.analyze(testUser, List.of(buildFd("SHORT_TERM"))))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Connection refused");
        }
    }
}