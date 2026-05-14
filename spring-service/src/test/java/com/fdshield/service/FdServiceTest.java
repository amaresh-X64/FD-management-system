package com.fdshield.service;

import com.fdshield.client.FastApiClient;
import com.fdshield.client.GoRiskClient;
import com.fdshield.dto.CreateFdRequest;
import com.fdshield.dto.FdResponse;
import com.fdshield.dto.WithdrawResponse;
import com.fdshield.entity.FixedDeposit;
import com.fdshield.entity.User;
import com.fdshield.entity.WithdrawalLog;
import com.fdshield.repository.FixedDepositRepository;
import com.fdshield.repository.UserFinancialProfileRepository;
import com.fdshield.repository.UserRepository;
import com.fdshield.repository.WithdrawalLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
        testUser = User.builder()
                .id(1L)
                .name("Arjun Kumar")
                .email("arjun@example.com")
                .monthlyIncome(new BigDecimal("80000"))
                .monthlyExpenses(new BigDecimal("40000"))
                .build();
    }

    static Stream<Arguments> maturityAmountCases() {
        return Stream.of(
                Arguments.of(
                        "P=100000, r=7.5%, t=12 months → 107500",
                        new BigDecimal("100000"), new BigDecimal("7.5"), 12,
                        new BigDecimal("107500.00"), new BigDecimal("107500.00")),
                Arguments.of(
                        "P=100000, r=7.5%, t=24 months → 115562.50",
                        new BigDecimal("100000"), new BigDecimal("7.5"), 24,
                        new BigDecimal("115562.50"), new BigDecimal("115562.50")),
                Arguments.of(
                        "Zero interest rate returns principal unchanged",
                        new BigDecimal("100000"), BigDecimal.ZERO, 12,
                        new BigDecimal("100000.00"), new BigDecimal("100000.00")),
                Arguments.of(
                        "Partial year: 6 months computes correctly (range 103900–103950)",
                        new BigDecimal("100000"), new BigDecimal("8.0"), 6,
                        new BigDecimal("103900.00"), new BigDecimal("103950.00")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maturityAmountCases")
    @DisplayName("calculateMaturityAmount – compound interest formula")
    void calculateMaturityAmount(
            String description,
            BigDecimal principal, BigDecimal rate, int months,
            BigDecimal expectedMin, BigDecimal expectedMax) {

        BigDecimal result = fdService.calculateMaturityAmount(principal, rate, months);

        if (expectedMin.compareTo(expectedMax) == 0) {
            assertThat(result).isEqualByComparingTo(expectedMin);
        } else {
            assertThat(result).isBetween(expectedMin, expectedMax);
        }
    }

    static Stream<Arguments> fdTypeCases() {
        return Stream.of(
                Arguments.of("12-month FD should be SHORT_TERM", 12, "SHORT_TERM"),
                Arguments.of("13-month FD should be LONG_TERM", 13, "LONG_TERM"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fdTypeCases")
    @DisplayName("saveFd – FD type assignment by duration")
    void createFd_fdTypeAssignment(String description, int durationMonths, String expectedFdType) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.save(any())).thenAnswer(inv -> {
            FixedDeposit fd = inv.getArgument(0);
            return FixedDeposit.builder()
                    .id(1L).user(testUser)
                    .principal(fd.getPrincipal()).interestRate(fd.getInterestRate())
                    .durationMonths(fd.getDurationMonths()).maturityAmount(fd.getMaturityAmount())
                    .startDate(fd.getStartDate()).maturityDate(fd.getMaturityDate())
                    .fdType(fd.getFdType()).status(fd.getStatus()).build();
        });

        CreateFdRequest req = new CreateFdRequest(1L, new BigDecimal("200000"),
                new BigDecimal("7.5"), durationMonths, LocalDate.of(2025, 1, 1));

        FdResponse response = fdService.saveFd(req);

        assertThat(response.fdType).isEqualTo(expectedFdType);
    }

    static Stream<Arguments> maturityDateCases() {
        return Stream.of(
                Arguments.of(
                        "start=2025-01-01, 24 months → maturity=2027-01-01",
                        LocalDate.of(2025, 1, 1), 24, LocalDate.of(2027, 1, 1)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("maturityDateCases")
    @DisplayName("saveFd – maturity date = start date + duration months")
    void createFd_maturityDate(String description, LocalDate startDate,
            int durationMonths, LocalDate expectedMaturityDate) {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fdRepository.save(any())).thenAnswer(inv -> {
            FixedDeposit fd = inv.getArgument(0);
            return FixedDeposit.builder()
                    .id(1L).user(testUser)
                    .principal(fd.getPrincipal()).interestRate(fd.getInterestRate())
                    .durationMonths(fd.getDurationMonths()).maturityAmount(fd.getMaturityAmount())
                    .startDate(fd.getStartDate()).maturityDate(fd.getMaturityDate())
                    .fdType(fd.getFdType()).status(fd.getStatus()).build();
        });

        CreateFdRequest req = new CreateFdRequest(1L, new BigDecimal("200000"),
                new BigDecimal("7.5"), durationMonths, startDate);

        FdResponse response = fdService.saveFd(req);

        assertThat(response.maturityDate).isEqualTo(expectedMaturityDate);
    }

    static Stream<Arguments> userNotFoundCases() {
        return Stream.of(
                Arguments.of(
                        "non-existent userId=99 throws RuntimeException with message 'User not found: 99'",
                        99L, "User not found: 99"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("userNotFoundCases")
    @DisplayName("saveFd – non-existent user throws RuntimeException")
    void createFd_userNotFound(String description, long userId, String expectedMessage) {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        CreateFdRequest req = new CreateFdRequest(userId, new BigDecimal("200000"),
                new BigDecimal("7.5"), 12, LocalDate.of(2025, 1, 1));

        assertThatThrownBy(() -> fdService.saveFd(req))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> withdrawalCalculationCases() {
        return Stream.of(
                Arguments.of(
                        "Penalty = 1% of principal (200000 → 2000)",
                        new BigDecimal("200000"), new BigDecimal("7.5"), 24,
                        LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1), "LONG_TERM",
                        new BigDecimal("2000.00")),
                Arguments.of(
                        "Net payout = principal + interest - penalty (100000, 7.5%, 12 months)",
                        new BigDecimal("100000"), new BigDecimal("7.5"), 12,
                        LocalDate.now().minusMonths(6), LocalDate.now().plusMonths(6), "SHORT_TERM",
                        null // net payout is validated relationally, not by a fixed value
                ));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawalCalculationCases")
    @DisplayName("saveWithdrawal – penalty and net payout calculations")
    void withdrawFd_calculations(
            String description,
            BigDecimal principal, BigDecimal rate, int months,
            LocalDate startDate, LocalDate maturityDate, String fdType,
            BigDecimal expectedPenalty) {

        FixedDeposit fd = FixedDeposit.builder()
                .id(1L).user(testUser)
                .principal(principal).interestRate(rate)
                .durationMonths(months)
                .startDate(startDate).maturityDate(maturityDate)
                .fdType(fdType).status("ACTIVE")
                .build();

        when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
        when(fdRepository.save(any())).thenReturn(fd);
        when(withdrawalLogRepository.save(any())).thenReturn(new WithdrawalLog());

        WithdrawResponse response = fdService.saveWithdrawal(1L);

        if (expectedPenalty != null) {
            assertThat(response.penaltyAmount).isEqualByComparingTo(expectedPenalty);
        } else {
            BigDecimal expected = response.principal
                    .add(response.interestEarned)
                    .subtract(response.penaltyAmount);
            assertThat(response.netPayout).isEqualByComparingTo(expected);
        }
    }

    static Stream<Arguments> withdrawalExceptionCases() {
        return Stream.of(
                Arguments.of(
                        "WITHDRAWN status throws RuntimeException with 'FD is not active'",
                        "WITHDRAWN", "FD is not active"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawalExceptionCases")
    @DisplayName("saveWithdrawal – non-active FD throws RuntimeException")
    void withdrawFd_nonActiveFd_throwsException(
            String description, String fdStatus, String expectedMessage) {

        FixedDeposit fd = FixedDeposit.builder()
                .id(1L).user(testUser)
                .principal(new BigDecimal("100000"))
                .interestRate(new BigDecimal("7.5"))
                .durationMonths(12)
                .startDate(LocalDate.of(2024, 1, 1))
                .maturityDate(LocalDate.of(2025, 1, 1))
                .fdType("SHORT_TERM").status(fdStatus)
                .build();

        when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));

        assertThatThrownBy(() -> fdService.saveWithdrawal(1L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining(expectedMessage);
    }

    static Stream<Arguments> withdrawalSideEffectCases() {
        return Stream.of(
                Arguments.of(
                        "Withdrawal sets FD status to WITHDRAWN",
                        new BigDecimal("200000"), "LONG_TERM", "WITHDRAWN", "PREMATURE"),
                Arguments.of(
                        "Withdrawal logs a PREMATURE withdrawal record",
                        new BigDecimal("200000"), "LONG_TERM", "WITHDRAWN", "PREMATURE"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("withdrawalSideEffectCases")
    @DisplayName("saveWithdrawal – status update and withdrawal log side effects")
    void withdrawFd_sideEffects(
            String description,
            BigDecimal principal, String fdType,
            String expectedStatus, String expectedWithdrawalType) {

        FixedDeposit fd = FixedDeposit.builder()
                .id(1L).user(testUser)
                .principal(principal)
                .interestRate(new BigDecimal("7.5"))
                .durationMonths(24)
                .startDate(LocalDate.of(2024, 1, 1))
                .maturityDate(LocalDate.of(2026, 1, 1))
                .fdType(fdType).status("ACTIVE")
                .build();

        when(fdRepository.findById(1L)).thenReturn(Optional.of(fd));
        when(fdRepository.save(any())).thenReturn(fd);
        when(withdrawalLogRepository.save(any())).thenReturn(new WithdrawalLog());

        fdService.saveWithdrawal(1L);

        verify(fdRepository).save(argThat(saved -> expectedStatus.equals(saved.getStatus())));
        verify(withdrawalLogRepository).save(argThat(log -> expectedWithdrawalType.equals(log.getWithdrawalType())));
    }
}