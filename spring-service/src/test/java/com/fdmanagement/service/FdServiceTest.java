package com.fdmanagement.service;

import com.fdmanagement.client.FastApiClient;
import com.fdmanagement.client.GoRiskClient;
import com.fdmanagement.dto.*;
import com.fdmanagement.entity.*;
import com.fdmanagement.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdServiceTest {

    @Mock private FixedDepositRepository fdRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserFinancialProfileRepository profileRepository;
    @Mock private WithdrawalLogRepository withdrawalLogRepository;
    @Mock private GoRiskClient goRiskClient;
    @Mock private FastApiClient fastApiClient;

    @InjectMocks private FdService fdService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Arjun Kumar")
                .email("arjun@example.com")
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


    static Stream<Arguments> maturityCases() {
        return Stream.of(
            Arguments.of("1 year 7.5%",  "100000", "7.5",  12, "107500.00",  "107500.00"),
            Arguments.of("2 years 7.5%", "100000", "7.5",  24, "115562.50",  "115562.50"),
            Arguments.of("0% rate",       "100000", "0",   12, "100000.00",  "100000.00"),
            Arguments.of("6 months 8%",   "100000", "8.0",  6, "103900.00",  "103950.00")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maturityCases")
    @DisplayName("calculateMaturityAmount – compound interest formula")
    void calculateMaturityAmount(String label, String p, String r, int months,
                                  String min, String max) {
        BigDecimal result = fdService.calculateMaturityAmount(
                new BigDecimal(p), new BigDecimal(r), months);
        if (min.equals(max))
            assertThat(result).isEqualByComparingTo(new BigDecimal(min));
        else
            assertThat(result).isBetween(new BigDecimal(min), new BigDecimal(max));
    }


    static Stream<Arguments> fdTypeCases() {
        return Stream.of(
            Arguments.of("12 months → SHORT_TERM", 12, "SHORT_TERM"),
            Arguments.of("13 months → LONG_TERM",  13, "LONG_TERM")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fdTypeCases")
    @DisplayName("saveFd – FD type assigned by duration")
    void saveFd_fdType(String label, int months, String expected) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.save(any())).thenAnswer(inv -> savedFd(inv.getArgument(0)));

        FdResponse r = fdService.saveFd(new CreateFdRequest(
                1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                months, LocalDate.of(2025, 1, 1)));

        assertThat(r.fdType).isEqualTo(expected);
    }

    @Test
    @DisplayName("saveFd – maturity date = start + duration months")
    void saveFd_maturityDate() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.save(any())).thenAnswer(inv -> savedFd(inv.getArgument(0)));

        FdResponse r = fdService.saveFd(new CreateFdRequest(
                1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                24, LocalDate.of(2025, 1, 1)));

        assertThat(r.maturityDate).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    @DisplayName("saveFd – unknown user throws RuntimeException")
    void saveFd_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> fdService.saveFd(new CreateFdRequest(
                99L, new BigDecimal("200000"), new BigDecimal("7.5"),
                12, LocalDate.of(2025, 1, 1))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found: 99");
    }


    @Test
    @DisplayName("saveWithdrawal – penalty = 1% of principal")
    void withdrawal_penalty() {
        FixedDeposit fd = activeFd(new BigDecimal("200000"), "LONG_TERM",
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
        when(fdRepository.save(any())).thenReturn(fd);
        when(withdrawalLogRepository.save(any())).thenReturn(new WithdrawalLog());

        assertThat(fdService.saveWithdrawal(1L).penaltyAmount)
                .isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("saveWithdrawal – net payout = principal + interest - penalty")
    void withdrawal_netPayout() {
        FixedDeposit fd = activeFd(new BigDecimal("100000"), "SHORT_TERM",
                LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6));
        when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
        when(fdRepository.save(any())).thenReturn(fd);
        when(withdrawalLogRepository.save(any())).thenReturn(new WithdrawalLog());

        WithdrawResponse r = fdService.saveWithdrawal(1L);
        assertThat(r.netPayout)
                .isEqualByComparingTo(r.principal.add(r.interestEarned).subtract(r.penaltyAmount));
    }

    static Stream<Arguments> withdrawalSideEffectCases() {
        return Stream.of(
            Arguments.of("Sets status to WITHDRAWN", "WITHDRAWN"),
            Arguments.of("Logs PREMATURE withdrawal type", "PREMATURE")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawalSideEffectCases")
    @DisplayName("saveWithdrawal – status and log side effects")
    void withdrawal_sideEffects(String label, String expected) {
        FixedDeposit fd = activeFd(new BigDecimal("200000"), "LONG_TERM",
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));
        when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
        when(fdRepository.save(any())).thenReturn(fd);
        when(withdrawalLogRepository.save(any())).thenReturn(new WithdrawalLog());

        fdService.saveWithdrawal(1L);

        if (expected.equals("WITHDRAWN"))
            verify(fdRepository).save(argThat(f -> "WITHDRAWN".equals(f.getStatus())));
        else
            verify(withdrawalLogRepository).save(argThat(l -> "PREMATURE".equals(l.getWithdrawalType())));
    }

    static Stream<Arguments> withdrawalExceptionCases() {
        return Stream.of(
            Arguments.of("WITHDRAWN status → exception", "WITHDRAWN"),
            Arguments.of("MATURED status → exception",   "MATURED")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawalExceptionCases")
    @DisplayName("saveWithdrawal – non-ACTIVE FD throws RuntimeException")
    void withdrawal_nonActive_throws(String label, String status) {
        FixedDeposit fd = activeFd(new BigDecimal("100000"), "SHORT_TERM",
                LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1));
        fd.setStatus(status);
        when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));

        assertThatThrownBy(() -> fdService.saveWithdrawal(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("FD is not active");
    }

    static Stream<Arguments> portfolioSnapshotCases() {
        return Stream.of(
            Arguments.of("No profile → null snapshot", Optional.empty(), true),
            Arguments.of("With profile → populated snapshot", Optional.of(buildProfile()), false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("portfolioSnapshotCases")
    @DisplayName("getPortfolio – financial profile null vs populated")
    void getPortfolio_snapshot(String label,
                                Optional<UserFinancialProfile> profile,
                                boolean expectNull) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
        when(profileRepository.findById(1L)).thenReturn(profile);

        PortfolioResponse r = fdService.getPortfolio(1L);
        if (expectNull)
            assertThat(r.financialProfile).isNull();
        else
            assertThat(r.financialProfile.persona).isEqualTo("Conservative Saver");
    }

    static Stream<Arguments> portfolioTotalsCases() {
        return Stream.of(
            Arguments.of("No FDs → zero totals",  List.of(),         "0",      "0"),
            Arguments.of("One FD → summed totals", null, "200000", "215000.00")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("portfolioTotalsCases")
    @DisplayName("getPortfolio – totals summed correctly")
    void getPortfolio_totals(String label, List<FixedDeposit> fds,
                              String expectedPrincipal, String expectedMaturity) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        List<FixedDeposit> list = fds != null ? fds : List.of(
                activeFd(new BigDecimal("200000"), "SHORT_TERM",
                        LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1)));
        if (!list.isEmpty()) list.get(0).setMaturityAmount(new BigDecimal("215000.00"));
        when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(list);
        when(profileRepository.findById(1L)).thenReturn(Optional.empty());

        PortfolioResponse r = fdService.getPortfolio(1L);
        assertThat(r.totalPrincipal).isEqualByComparingTo(new BigDecimal(expectedPrincipal));
        assertThat(r.totalMaturityValue).isEqualByComparingTo(new BigDecimal(expectedMaturity));
    }

    @Test
    @DisplayName("getPortfolio – unknown user throws RuntimeException")
    void getPortfolio_userNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> fdService.getPortfolio(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("User not found: 99");
    }


    @Test
    @DisplayName("createFd – Go service failure does not prevent FD creation")
    void createFd_analyticsFailureDoesNotRollback() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.save(any())).thenAnswer(inv -> savedFd(inv.getArgument(0)));
        when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());
        when(goRiskClient.analyze(any(), any()))
                .thenThrow(new RuntimeException("Go service down"));

        FdResponse r = fdService.createFd(new CreateFdRequest(
                1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                12, LocalDate.of(2025, 1, 1)));

        assertThat(r).isNotNull();
        assertThat(r.status).isEqualTo("ACTIVE");
    }


    @Test
    @DisplayName("withdrawFd – outer wrapper calls saveWithdrawal and triggers analytics")
    void withdrawFd_outerWrapper_callsAnalyticsRefresh() {
        FixedDeposit fd = activeFd(new BigDecimal("200000"), "LONG_TERM",
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1));

        when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
        when(fdRepository.save(any())).thenReturn(fd);
        when(withdrawalLogRepository.save(any())).thenReturn(new WithdrawalLog());
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());

        WithdrawResponse r = fdService.withdrawFd(1L);

        assertThat(r).isNotNull();
        assertThat(r.penaltyAmount).isEqualByComparingTo(new BigDecimal("2000.00"));
    }

    @Test
    @DisplayName("withdrawFd – FD not found throws RuntimeException")
    void withdrawFd_fdNotFound_throws() {
        when(fdRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> fdService.withdrawFd(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("FD not found: 99");
    }


    @Test
    @DisplayName("createFd – analytics refresh updates existing profile when one already exists")
    void createFd_analyticsRefresh_updatesExistingProfile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.save(any())).thenAnswer(inv -> savedFd(inv.getArgument(0)));
        when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());

        // existing profile already in DB
        when(profileRepository.findById(1L)).thenReturn(Optional.of(buildProfile()));

        GoRiskClient.RiskResult risk = new GoRiskClient.RiskResult();
        risk.liquidityScore      = new BigDecimal("75");
        risk.maturitySpreadScore = new BigDecimal("65");
        risk.penaltyExposure     = new BigDecimal("25");
        risk.concentrationRisk   = new BigDecimal("70");
        risk.ladderScore         = new BigDecimal("80");
        when(goRiskClient.analyze(any(), any())).thenReturn(risk);

        FastApiClient.AnalyticsResult analytics = new FastApiClient.AnalyticsResult();
        analytics.persona              = "Conservative Saver";
        analytics.portfolioHealthScore = new BigDecimal("78");
        analytics.recommendation       = "Looking good";
        when(fastApiClient.generate(any(), any(), any())).thenReturn(analytics);
        when(profileRepository.save(any())).thenReturn(buildProfile());

        FdResponse r = fdService.createFd(new CreateFdRequest(
                1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                12, LocalDate.of(2025, 1, 1)));

        assertThat(r).isNotNull();
        // verify profile was saved with updated values
        verify(profileRepository).save(argThat(p ->
                "Conservative Saver".equals(p.getPersona())));
    }

    @Test
    @DisplayName("createFd – analytics refresh creates new profile when none exists")
    void createFd_analyticsRefresh_createsNewProfile() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.save(any())).thenAnswer(inv -> savedFd(inv.getArgument(0)));
        when(fdRepository.findActiveFdsByUserId(1L)).thenReturn(List.of());

        // no existing profile
        when(profileRepository.findById(1L)).thenReturn(Optional.empty());

        GoRiskClient.RiskResult risk = new GoRiskClient.RiskResult();
        risk.liquidityScore      = new BigDecimal("60");
        risk.maturitySpreadScore = new BigDecimal("50");
        risk.penaltyExposure     = new BigDecimal("30");
        risk.concentrationRisk   = new BigDecimal("60");
        risk.ladderScore         = new BigDecimal("55");
        when(goRiskClient.analyze(any(), any())).thenReturn(risk);

        FastApiClient.AnalyticsResult analytics = new FastApiClient.AnalyticsResult();
        analytics.persona              = "Balanced Investor";
        analytics.portfolioHealthScore = new BigDecimal("65");
        analytics.recommendation       = "Add short-term FDs";
        when(fastApiClient.generate(any(), any(), any())).thenReturn(analytics);
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FdResponse r = fdService.createFd(new CreateFdRequest(
                1L, new BigDecimal("200000"), new BigDecimal("7.5"),
                12, LocalDate.of(2025, 1, 1)));

        assertThat(r).isNotNull();
        verify(profileRepository).save(argThat(p ->
                "Balanced Investor".equals(p.getPersona())));
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
}