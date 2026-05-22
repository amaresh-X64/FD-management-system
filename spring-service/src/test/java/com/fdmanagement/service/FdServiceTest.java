package com.fdmanagement.service;

import com.fdmanagement.client.FastApiClient;
import com.fdmanagement.client.GoRiskClient;
import com.fdmanagement.dto.*;
import com.fdmanagement.entity.*;
import com.fdmanagement.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdServiceTest {

    @Mock
    private FixedDepositRepository fdRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserFinancialProfileRepository profileRepository;
    @Mock
    private WithdrawalLogRepository withdrawalLogRepository;
    @Mock
    private GoRiskClient goRiskClient;
    @Mock
    private FastApiClient fastApiClient;

    @InjectMocks
    private FdService fdService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Amaresh")
                .email("lakshman@gmail.com")
                .monthlyIncome(new BigDecimal("80000"))
                .monthlyExpenses(new BigDecimal("40000")).build();
    }

    private FixedDeposit savedFd(FixedDeposit fd) {
        return FixedDeposit.builder().id(1L).user(testUser)
                .principal(fd.getPrincipal()).interestRate(fd.getInterestRate())
                .durationMonths(fd.getDurationMonths()).maturityAmount(fd.getMaturityAmount())
                .startDate(fd.getStartDate()).maturityDate(fd.getMaturityDate())
                .fdType(fd.getFdType()).status(fd.getStatus()).build();
    }

    private FixedDeposit activeFd(BigDecimal principal, String type,
            LocalDate start, LocalDate maturity) {
        return FixedDeposit.builder().id(1L).user(testUser)
                .principal(principal).interestRate(new BigDecimal("7.5"))
                .maturityAmount(new BigDecimal("215000"))
                .durationMonths(24).startDate(start).maturityDate(maturity)
                .fdType(type).status("ACTIVE").build();
    }

    private FixedDeposit activeFdWithId(long id, BigDecimal principal, String type,
            LocalDate start, LocalDate maturity) {
        return FixedDeposit.builder().id(id).user(testUser)
                .principal(principal).interestRate(new BigDecimal("7.5"))
                .maturityAmount(new BigDecimal("215000"))
                .durationMonths(24).startDate(start).maturityDate(maturity)
                .fdType(type).status("ACTIVE").build();
    }

    private static UserFinancialProfile buildProfile() {
        return UserFinancialProfile.builder().userId(1L)
                .persona("Conservative Saver")
                .liquidityScore(new BigDecimal("80"))
                .maturitySpreadScore(new BigDecimal("70"))
                .penaltyRisk(new BigDecimal("20"))
                .concentrationRisk(new BigDecimal("65"))
                .ladderScore(new BigDecimal("75"))
                .portfolioHealthScore(new BigDecimal("77"))
                .recommendation("Healthy portfolio").build();
    }

    private void stubAnalytics(String persona) {
        GoRiskClient.RiskResult risk = new GoRiskClient.RiskResult();
        risk.liquidityScore = new BigDecimal("75");
        risk.maturitySpreadScore = new BigDecimal("65");
        risk.penaltyExposure = new BigDecimal("25");
        risk.concentrationRisk = new BigDecimal("70");
        risk.ladderScore = new BigDecimal("80");
        when(goRiskClient.analyze(eq(testUser), anyList())).thenReturn(risk);

        FastApiClient.AnalyticsResult analytics = new FastApiClient.AnalyticsResult();
        analytics.persona = persona;
        analytics.portfolioHealthScore = new BigDecimal("78");
        analytics.recommendation = "Looking good";
        when(fastApiClient.generate(eq(testUser), anyList(), eq(risk))).thenReturn(analytics);
    }

    @Nested
    @DisplayName("calculateMaturityAmount")
    class CalculateMaturityAmount {

        @Test
        void shouldReturnCompoundedAmount_whenInputIsOneYearAt7Point5Percent() {
            BigDecimal result = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("7.5"), 12);
            assertThat(result).isEqualByComparingTo(new BigDecimal("107500.00"));
        }

        @Test
        void shouldReturnCompoundedAmount_whenInputIsTwoYearsAt7Point5Percent() {
            BigDecimal result = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("7.5"), 24);
            assertThat(result).isEqualByComparingTo(new BigDecimal("115562.50"));
        }

        @Test
        void shouldReturnPrincipalUnchanged_whenInputIsZeroInterestRate() {
            BigDecimal result = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("0"), 12);
            assertThat(result).isEqualByComparingTo(new BigDecimal("100000.00"));
        }

        @Test
        void shouldReturnAmountWithinExpectedRange_whenInputIsSixMonthsAt8Percent() {
            BigDecimal result = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("8.0"), 6);
            assertThat(result).isBetween(new BigDecimal("103900.00"), new BigDecimal("103950.00"));
        }

        @Test
        void shouldReturnHigherAmount_whenInputIsLongerDurationAtSameRate() {
            BigDecimal shortTerm = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("7.5"), 12);
            BigDecimal longTerm = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("7.5"), 36);
            assertThat(longTerm).isGreaterThan(shortTerm);
        }

        @Test
        void shouldReturnHigherAmount_whenInputIsHigherInterestRateAtSameDuration() {
            BigDecimal low = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("5.0"), 12);
            BigDecimal high = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("10.0"), 12);
            assertThat(high).isGreaterThan(low);
        }

        @Test
        void shouldScaleLinearly_whenInputIsDoubledPrincipalAtSameRateAndDuration() {
            BigDecimal single = fdService.calculateMaturityAmount(
                    new BigDecimal("100000"), new BigDecimal("7.5"), 12);
            BigDecimal doubled = fdService.calculateMaturityAmount(
                    new BigDecimal("200000"), new BigDecimal("7.5"), 12);
            assertThat(doubled).isEqualByComparingTo(single.multiply(new BigDecimal("2")));
        }
    }

    @Nested
    @DisplayName("saveFd")
    class SaveFd {

        @Test
        void shouldAssignShortTermType_whenInputIsDurationOfExactly12Months() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));

            FdResponse r = fdService.saveFd(new CreateFdRequest(
                    1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    12, LocalDate.of(2025, 1, 1)));

            assertThat(r.fdType).isEqualTo("SHORT_TERM");
        }

        @Test
        void shouldAssignShortTermType_whenInputIsDurationOfExactly1Month() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));

            FdResponse r = fdService.saveFd(new CreateFdRequest(
                    1L, new BigDecimal("100000"), new BigDecimal("7.5"),
                    1, LocalDate.of(2025, 1, 1)));

            assertThat(r.fdType).isEqualTo("SHORT_TERM");
        }

        @Test
        void shouldAssignLongTermType_whenInputIsDurationOf13Months() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));

            FdResponse r = fdService.saveFd(new CreateFdRequest(
                    1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    13, LocalDate.of(2025, 1, 1)));

            assertThat(r.fdType).isEqualTo("LONG_TERM");
        }

        @Test
        void shouldSetMaturityDateToStartPlusDuration_whenInputIsValidRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));

            FdResponse r = fdService.saveFd(new CreateFdRequest(
                    1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    24, LocalDate.of(2025, 1, 1)));

            assertThat(r.maturityDate).isEqualTo(LocalDate.of(2027, 1, 1));
        }

        @Test
        void shouldSetMaturityDateCorrectly_whenInputIs36MonthDuration() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));

            FdResponse r = fdService.saveFd(new CreateFdRequest(
                    1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    36, LocalDate.of(2025, 6, 1)));

            assertThat(r.maturityDate).isEqualTo(LocalDate.of(2028, 6, 1));
        }

        @Test
        void shouldSetStatusToActive_whenInputIsNewFdRequest() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));

            FdResponse r = fdService.saveFd(new CreateFdRequest(
                    1L, new BigDecimal("100000"), new BigDecimal("7.5"),
                    12, LocalDate.of(2025, 1, 1)));

            assertThat(r.status).isEqualTo("ACTIVE");
        }

        @Test
        void shouldThrowRuntimeException_whenInputIsUnknownUserId() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fdService.saveFd(new CreateFdRequest(
                    99L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    12, LocalDate.of(2025, 1, 1))))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found: 99");
        }
    }

    @Nested
    @DisplayName("saveWithdrawal")
    class SaveWithdrawal {

        @Test
        void shouldReturnOnePercentPenalty_whenInputIsActiveFdWithPrincipalOf200000() {
            FixedDeposit fd = activeFd(new BigDecimal("200000"), "LONG_TERM",
                    LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
            when(fdRepository.save(argThat(f -> "WITHDRAWN".equals(f.getStatus())))).thenReturn(fd);
            when(withdrawalLogRepository.save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType()))))
                    .thenReturn(new WithdrawalLog());

            assertThat(fdService.saveWithdrawal(1L).penaltyAmount)
                    .isEqualByComparingTo(new BigDecimal("2000.00"));
        }

        @Test
        void shouldReturnOnePercentPenalty_whenInputIsActiveFdWithPrincipalOf500000() {
            FixedDeposit fd = activeFd(new BigDecimal("500000"), "LONG_TERM",
                    LocalDate.of(2024, 1, 1), LocalDate.of(2027, 1, 1));
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
            when(fdRepository.save(argThat(f -> "WITHDRAWN".equals(f.getStatus())))).thenReturn(fd);
            when(withdrawalLogRepository.save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType()))))
                    .thenReturn(new WithdrawalLog());

            assertThat(fdService.saveWithdrawal(1L).penaltyAmount)
                    .isEqualByComparingTo(new BigDecimal("5000.00"));
        }

        @Test
        void shouldReturnNetPayoutEqualToPrincipalPlusInterestMinusPenalty_whenInputIsActiveFd() {
            FixedDeposit fd = activeFd(new BigDecimal("100000"), "SHORT_TERM",
                    LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6));
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
            when(fdRepository.save(argThat(f -> "WITHDRAWN".equals(f.getStatus())))).thenReturn(fd);
            when(withdrawalLogRepository.save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType()))))
                    .thenReturn(new WithdrawalLog());

            WithdrawResponse r = fdService.saveWithdrawal(1L);
            assertThat(r.netPayout)
                    .isEqualByComparingTo(r.principal.add(r.interestEarned).subtract(r.penaltyAmount));
        }

        @Test
        void shouldReturnNonNegativeNetPayout_whenInputIsFdHeldForSixMonths() {
            FixedDeposit fd = activeFd(new BigDecimal("100000"), "LONG_TERM",
                    LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(18));
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
            when(fdRepository.save(argThat(f -> "WITHDRAWN".equals(f.getStatus())))).thenReturn(fd);
            when(withdrawalLogRepository.save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType()))))
                    .thenReturn(new WithdrawalLog());

            assertThat(fdService.saveWithdrawal(1L).netPayout)
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }

        @Test
        void shouldReturnZeroInterestEarned_whenInputIsFdWithdrawnOnSameDayAsStart() {
            FixedDeposit fd = activeFd(new BigDecimal("100000"), "SHORT_TERM",
                    LocalDate.now(), LocalDate.now().plusMonths(12));
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
            when(fdRepository.save(argThat(f -> "WITHDRAWN".equals(f.getStatus())))).thenReturn(fd);
            when(withdrawalLogRepository.save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType()))))
                    .thenReturn(new WithdrawalLog());

            WithdrawResponse r = fdService.saveWithdrawal(1L);
            assertThat(r.interestEarned).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldSetStatusToWithdrawn_whenInputIsActiveFd() {
            FixedDeposit fd = activeFd(new BigDecimal("200000"), "LONG_TERM",
                    LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
            when(fdRepository.save(argThat(f -> "WITHDRAWN".equals(f.getStatus())))).thenReturn(fd);
            when(withdrawalLogRepository.save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType()))))
                    .thenReturn(new WithdrawalLog());

            fdService.saveWithdrawal(1L);

            verify(fdRepository).save(argThat(f -> "WITHDRAWN".equals(f.getStatus())));
        }

        @Test
        void shouldLogPrematureWithdrawalType_whenInputIsActiveFd() {
            FixedDeposit fd = activeFd(new BigDecimal("200000"), "LONG_TERM",
                    LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
            when(fdRepository.save(argThat(f -> "WITHDRAWN".equals(f.getStatus())))).thenReturn(fd);
            when(withdrawalLogRepository.save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType()))))
                    .thenReturn(new WithdrawalLog());

            fdService.saveWithdrawal(1L);

            verify(withdrawalLogRepository).save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType())));
        }

        @Test
        void shouldThrowRuntimeException_whenInputIsFdWithStatusWithdrawn() {
            FixedDeposit fd = activeFd(new BigDecimal("100000"), "SHORT_TERM",
                    LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
            fd.setStatus("WITHDRAWN");
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));

            assertThatThrownBy(() -> fdService.saveWithdrawal(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("FD is not active");
        }

        @Test
        void shouldThrowRuntimeException_whenInputIsFdWithStatusMatured() {
            FixedDeposit fd = activeFd(new BigDecimal("100000"), "SHORT_TERM",
                    LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
            fd.setStatus("MATURED");
            when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));

            assertThatThrownBy(() -> fdService.saveWithdrawal(1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("FD is not active");
        }

        @Test
        void shouldThrowRuntimeException_whenInputIsUnknownFdId() {
            when(fdRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fdService.saveWithdrawal(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("FD not found: 99");
        }
    }

    @Nested
    @DisplayName("getPortfolio")
    class GetPortfolio {

        @Test
        void shouldReturnNullSnapshot_whenInputIsUserWithNoFinancialProfile() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
            when(profileRepository.findById(1L)).thenReturn(Optional.empty());

            assertThat(fdService.getPortfolio(1L).financialProfile).isNull();
        }

        @Test
        void shouldReturnPopulatedSnapshot_whenInputIsUserWithExistingProfile() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
            when(profileRepository.findById(1L)).thenReturn(Optional.of(buildProfile()));

            assertThat(fdService.getPortfolio(1L).financialProfile.persona)
                    .isEqualTo("Conservative Saver");
        }

        @Test
        void shouldReturnZeroTotals_whenInputIsUserWithNoActiveFds() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
            when(profileRepository.findById(1L)).thenReturn(Optional.empty());

            PortfolioResponse r = fdService.getPortfolio(1L);
            assertThat(r.totalPrincipal).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.totalMaturityValue).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void shouldReturnSummedTotals_whenInputIsUserWithOneFd() {
            FixedDeposit fd = activeFd(new BigDecimal("200000"), "SHORT_TERM",
                    LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
            fd.setMaturityAmount(new BigDecimal("215000.00"));
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of(fd));
            when(profileRepository.findById(1L)).thenReturn(Optional.empty());

            PortfolioResponse r = fdService.getPortfolio(1L);
            assertThat(r.totalPrincipal).isEqualByComparingTo(new BigDecimal("200000"));
            assertThat(r.totalMaturityValue).isEqualByComparingTo(new BigDecimal("215000.00"));
        }

        @Test
        void shouldReturnSummedPrincipal_whenInputIsUserWithThreeActiveFds() {
            FixedDeposit fd1 = activeFdWithId(1L, new BigDecimal("100000"), "SHORT_TERM",
                    LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
            fd1.setMaturityAmount(new BigDecimal("107500.00"));
            FixedDeposit fd2 = activeFdWithId(2L, new BigDecimal("200000"), "LONG_TERM",
                    LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1));
            fd2.setMaturityAmount(new BigDecimal("231250.00"));
            FixedDeposit fd3 = activeFdWithId(3L, new BigDecimal("150000"), "LONG_TERM",
                    LocalDate.of(2025, 1, 1), LocalDate.of(2028, 1, 1));
            fd3.setMaturityAmount(new BigDecimal("178500.00"));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of(fd1, fd2, fd3));
            when(profileRepository.findById(1L)).thenReturn(Optional.empty());

            PortfolioResponse r = fdService.getPortfolio(1L);
            assertThat(r.totalPrincipal).isEqualByComparingTo(new BigDecimal("450000"));
            assertThat(r.totalMaturityValue).isEqualByComparingTo(new BigDecimal("517250.00"));
        }

        @Test
        void shouldReturnAllFdsInList_whenInputIsUserWithTwoActiveFds() {
            FixedDeposit fd1 = activeFdWithId(1L, new BigDecimal("100000"), "SHORT_TERM",
                    LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
            FixedDeposit fd2 = activeFdWithId(2L, new BigDecimal("200000"), "LONG_TERM",
                    LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1));

            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of(fd1, fd2));
            when(profileRepository.findById(1L)).thenReturn(Optional.empty());

            assertThat(fdService.getPortfolio(1L).activeFds).hasSize(2);
        }

        @Test
        void shouldThrowRuntimeException_whenInputIsUnknownUserId() {
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> fdService.getPortfolio(99L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("User not found: 99");
        }
    }

    @Nested
    @DisplayName("triggerAnalyticsRefresh")
    class TriggerAnalyticsRefresh {

        @Test
        void shouldStillReturnFdResponse_whenInputIsGoServiceThatThrowsException() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
            when(goRiskClient.analyze(eq(testUser), anyList()))
                    .thenThrow(new RuntimeException("Go service down"));

            FdResponse r = fdService.createFd(new CreateFdRequest(
                    1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    12, LocalDate.of(2025, 1, 1)));

            assertThat(r).isNotNull();
            assertThat(r.status).isEqualTo("ACTIVE");
        }

        @Test
        void shouldStillReturnFdResponse_whenInputIsFastApiServiceThatThrowsException() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
            GoRiskClient.RiskResult risk = new GoRiskClient.RiskResult();
            when(goRiskClient.analyze(eq(testUser), anyList())).thenReturn(risk);
            when(fastApiClient.generate(eq(testUser), anyList(), eq(risk)))
                    .thenThrow(new RuntimeException("FastAPI down"));

            FdResponse r = fdService.createFd(new CreateFdRequest(
                    1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    12, LocalDate.of(2025, 1, 1)));

            assertThat(r).isNotNull();
            assertThat(r.status).isEqualTo("ACTIVE");
        }

        @Test
        void shouldSaveProfileWithPersona_whenInputIsNewUserWithNoExistingProfile() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
            when(profileRepository.findById(1L)).thenReturn(Optional.empty());
            when(profileRepository.save(argThat(p -> "Balanced Investor".equals(p.getPersona()))))
                    .thenAnswer(inv -> inv.getArgument(0));
            stubAnalytics("Balanced Investor");

            fdService.createFd(new CreateFdRequest(
                    1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    12, LocalDate.of(2025, 1, 1)));

            verify(profileRepository).save(argThat(p -> "Balanced Investor".equals(p.getPersona())));
        }

        @Test
        void shouldUpdateProfileWithPersona_whenInputIsUserWithExistingProfile() {
            when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
            when(fdRepository.save(argThat(fd -> fd.getUser().equals(testUser))))
                    .thenAnswer(inv -> savedFd(inv.getArgument(0)));
            when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
            when(profileRepository.findById(1L)).thenReturn(Optional.of(buildProfile()));
            when(profileRepository.save(argThat(p -> "Conservative Saver".equals(p.getPersona()))))
                    .thenReturn(buildProfile());
            stubAnalytics("Conservative Saver");

            fdService.createFd(new CreateFdRequest(
                    1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                    12, LocalDate.of(2025, 1, 1)));

            verify(profileRepository).save(argThat(p -> "Conservative Saver".equals(p.getPersona())));
        }
    }
}