package com.fdmanagement.client;

import com.fdmanagement.entity.FixedDeposit;
import com.fdmanagement.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
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
				.id(1L).name("Arjun").email("lakshman@gmail.com")
				.monthlyIncome(new BigDecimal("80000"))
				.monthlyExpenses(new BigDecimal("40000")).build();

		riskResult = new GoRiskClient.RiskResult();
		riskResult.liquidityScore = BigDecimal.valueOf(70);
		riskResult.maturitySpreadScore = BigDecimal.valueOf(60);
		riskResult.penaltyExposure = BigDecimal.valueOf(20);
		riskResult.concentrationRisk = BigDecimal.valueOf(65);
		riskResult.ladderScore = BigDecimal.valueOf(70);
	}

	private static FixedDeposit buildFd() {
		return FixedDeposit.builder()
				.id(1L).principal(new BigDecimal("200000"))
				.maturityAmount(new BigDecimal("230000"))
				.interestRate(new BigDecimal("7.5")).durationMonths(24)
				.startDate(LocalDate.of(2025, 1, 1))
				.maturityDate(LocalDate.of(2027, 1, 1))
				.fdType("LONG_TERM").status("ACTIVE").build();
	}

	private static FastApiClient.AnalyticsResult buildResult(String persona, double health) {
		FastApiClient.AnalyticsResult r = new FastApiClient.AnalyticsResult();
		r.persona = persona;
		r.portfolioHealthScore = BigDecimal.valueOf(health);
		r.recommendation = "Keep it up";
		return r;
	}

	@Nested
	@DisplayName("generate")
	class Generate {

		@Test
		void shouldReturnDefaultUnknownPersona_whenInputIsNullResponseFromFastApi() {
			when(restTemplate.postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> body instanceof java.util.Map),
					eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(null);

			FastApiClient.AnalyticsResult result = client.generate(testUser, List.of(buildFd()),
					riskResult);

			assertThat(result.persona).isEqualTo("Unknown");
		}

		@Test
		void shouldReturnDefaultZeroHealthScore_whenInputIsNullResponseFromFastApi() {
			when(restTemplate.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(null);

			FastApiClient.AnalyticsResult result = client.generate(testUser, List.of(buildFd()),
					riskResult);

			assertThat(result.portfolioHealthScore).isEqualByComparingTo(BigDecimal.ZERO);
		}

		@Test
		void shouldReturnDefaultNonBlankRecommendation_whenInputIsNullResponseFromFastApi() {
			when(restTemplate.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(null);

			FastApiClient.AnalyticsResult result = client.generate(testUser, List.of(buildFd()),
					riskResult);

			assertThat(result.recommendation).isNotBlank();
		}

		@Test
		void shouldReturnMappedPersona_whenInputIsValidResponseFromFastApi() {
			when(restTemplate.postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> body instanceof java.util.Map),
					eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(buildResult("Conservative Saver", 85.0));

			FastApiClient.AnalyticsResult result = client.generate(testUser, List.of(buildFd()),
					riskResult);

			assertThat(result.persona).isEqualTo("Conservative Saver");
		}

		@Test
		void shouldReturnExactHealthScore_whenInputIsValidResponseFromFastApi() {
			FastApiClient.AnalyticsResult stub = new FastApiClient.AnalyticsResult();
			stub.portfolioHealthScore = new BigDecimal("92.5");
			stub.persona = "Balanced Investor";
			stub.recommendation = "Good job.";

			when(restTemplate.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(stub);

			FastApiClient.AnalyticsResult result = client.generate(testUser, List.of(buildFd()),
					riskResult);

			assertThat(result.portfolioHealthScore).isEqualByComparingTo(new BigDecimal("92.5"));
			assertThat(result.recommendation).isEqualTo("Good job.");
		}

		@Test
		void shouldCallCorrectEndpoint_whenInputIsValidRequest() {
			when(restTemplate.postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> body instanceof java.util.Map),
					eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(new FastApiClient.AnalyticsResult());

			client.generate(testUser, List.of(buildFd()), riskResult);

			verify(restTemplate).postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> body instanceof java.util.Map),
					eq(FastApiClient.AnalyticsResult.class));
		}

		@Test
		void shouldReturnNonNullResult_whenInputIsEmptyFdList() {
			when(restTemplate.postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> body instanceof java.util.Map),
					eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(new FastApiClient.AnalyticsResult());

			FastApiClient.AnalyticsResult result = client.generate(testUser, List.of(), riskResult);

			assertThat(result).isNotNull();
		}

		@Test
		void shouldPropagateException_whenInputIsRestTemplateThrowingRuntimeException() {
			when(restTemplate.postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> body instanceof java.util.Map),
					eq(FastApiClient.AnalyticsResult.class)))
					.thenThrow(new RuntimeException("Connection refused"));

			assertThatThrownBy(() -> client.generate(testUser, List.of(buildFd()), riskResult))
					.isInstanceOf(RuntimeException.class)
					.hasMessageContaining("Connection refused");
		}

		@Test
		void shouldPropagateHttpServerErrorException_whenInputIsFastApiReturning500() {
			when(restTemplate.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class)))
					.thenThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

			assertThatThrownBy(() -> client.generate(testUser, List.of(buildFd()), riskResult))
					.isInstanceOf(HttpServerErrorException.class);
		}

		@Test
		void shouldPropagateResourceAccessException_whenInputIsUnreachableFastApi() {
			when(restTemplate.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class)))
					.thenThrow(new ResourceAccessException("Connection refused"));

			assertThatThrownBy(() -> client.generate(testUser, List.of(buildFd()), riskResult))
					.isInstanceOf(ResourceAccessException.class)
					.hasMessageContaining("Connection refused");
		}

		@Test
		void shouldIncludeAllRiskScoresInPayload_whenInputIsValidRiskResult() {
			when(restTemplate.postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> {
						@SuppressWarnings("unchecked")
						var map = (java.util.Map<String, Object>) body;
						return map.containsKey("liquidityScore")
								&& map.containsKey("maturitySpreadScore")
								&& map.containsKey("penaltyExposure")
								&& map.containsKey("concentrationRisk")
								&& map.containsKey("ladderScore");
					}),
					eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(new FastApiClient.AnalyticsResult());

			client.generate(testUser, List.of(buildFd()), riskResult);

			verify(restTemplate).postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> body instanceof java.util.Map),
					eq(FastApiClient.AnalyticsResult.class));
		}

		@Test
		void shouldIncludeFdsKeyInPayload_whenInputContainsFds() {
			when(restTemplate.postForObject(
					eq("http://localhost:8082/analytics/generate"),
					argThat(body -> {
						@SuppressWarnings("unchecked")
						var map = (java.util.Map<String, Object>) body;
						return map.containsKey("fds");
					}),
					eq(FastApiClient.AnalyticsResult.class)))
					.thenReturn(new FastApiClient.AnalyticsResult());

			client.generate(testUser, List.of(buildFd()), riskResult);

			verify(restTemplate, times(1))
					.postForObject(anyString(), any(), eq(FastApiClient.AnalyticsResult.class));
		}
	}
}