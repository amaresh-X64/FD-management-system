package com.fdshield.service;

import com.fdshield.client.GoRiskClient;
import com.fdshield.client.FastApiClient;
import com.fdshield.dto.*;
import com.fdshield.entity.*;
import com.fdshield.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FdService {

        private final FixedDepositRepository fdRepository;
        private final UserRepository userRepository;
        private final UserFinancialProfileRepository profileRepository;
        private final WithdrawalLogRepository withdrawalLogRepository;
        private final GoRiskClient goRiskClient;
        private final FastApiClient fastApiClient;

        public BigDecimal calculateMaturityAmount(BigDecimal principal, BigDecimal annualRatePercent,
                        int durationMonths) {
                BigDecimal r = annualRatePercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                double t = durationMonths / 12.0;
                double maturity = principal.doubleValue() * Math.pow(1 + r.doubleValue(), t);
                return BigDecimal.valueOf(maturity).setScale(2, RoundingMode.HALF_UP);
        }

        public FdResponse createFd(CreateFdRequest req) {
                FdResponse response = saveFd(req);
                triggerAnalyticsRefresh(req.userId);
                return response;
        }

        @Transactional
        public FdResponse saveFd(CreateFdRequest req) {
                User user = userRepository.findById(req.userId)
                                .orElseThrow(() -> new RuntimeException("User not found: " + req.userId));

                BigDecimal maturityAmount = calculateMaturityAmount(req.principal, req.interestRate,
                                req.durationMonths);
                LocalDate maturityDate = req.startDate.plusMonths(req.durationMonths);
                String fdType = req.durationMonths <= 12 ? "SHORT_TERM" : "LONG_TERM";

                FixedDeposit fd = FixedDeposit.builder()
                                .user(user)
                                .principal(req.principal)
                                .interestRate(req.interestRate)
                                .durationMonths(req.durationMonths)
                                .maturityAmount(maturityAmount)
                                .startDate(req.startDate)
                                .maturityDate(maturityDate)
                                .fdType(fdType)
                                .status("ACTIVE")
                                .build();

                fd = fdRepository.save(fd);
                log.info("Created FD id={} for userId={} principal={} maturity={}",
                                fd.getId(), user.getId(), req.principal, maturityAmount);
                return toFdResponse(fd);
        }

        public WithdrawResponse withdrawFd(Long fdId) {
                WithdrawResponse response = saveWithdrawal(fdId);
                triggerAnalyticsRefresh(response.userId);
                return response;
        }

        @Transactional
        public WithdrawResponse saveWithdrawal(Long fdId) {
                FixedDeposit fd = fdRepository.findById(fdId)
                                .orElseThrow(() -> new RuntimeException("FD not found: " + fdId));

                if (!"ACTIVE".equals(fd.getStatus())) {
                        throw new RuntimeException("FD is not active. Current status: " + fd.getStatus());
                }

                long monthsElapsed = ChronoUnit.MONTHS.between(fd.getStartDate(), LocalDate.now());
                if (monthsElapsed < 0)
                        monthsElapsed = 0;

                BigDecimal penaltyRate = fd.getInterestRate().subtract(BigDecimal.ONE).max(BigDecimal.ZERO);
                BigDecimal interestEarned = calculateMaturityAmount(fd.getPrincipal(), penaltyRate, (int) monthsElapsed)
                                .subtract(fd.getPrincipal()).max(BigDecimal.ZERO);

                BigDecimal penaltyAmount = fd.getPrincipal()
                                .multiply(BigDecimal.valueOf(0.01))
                                .setScale(2, RoundingMode.HALF_UP);

                BigDecimal netPayout = fd.getPrincipal().add(interestEarned).subtract(penaltyAmount);

                fd.setStatus("WITHDRAWN");
                fdRepository.save(fd);

                withdrawalLogRepository.save(WithdrawalLog.builder()
                                .fixedDeposit(fd)
                                .withdrawalType("PREMATURE")
                                .penaltyAmount(penaltyAmount)
                                .build());

                log.info("Premature withdrawal fdId={} netPayout={} penalty={}", fdId, netPayout, penaltyAmount);

                return WithdrawResponse.builder()
                                .fdId(fdId)
                                .userId(fd.getUser().getId())
                                .principal(fd.getPrincipal())
                                .interestEarned(interestEarned)
                                .penaltyAmount(penaltyAmount)
                                .netPayout(netPayout)
                                .message("Premature withdrawal processed. Penalty of 1% applied.")
                                .build();
        }

        public PortfolioResponse getPortfolio(Long userId) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                List<FixedDeposit> activeFds = fdRepository.findActiveFdsByUserId(userId);

                BigDecimal totalPrincipal = activeFds.stream()
                                .map(FixedDeposit::getPrincipal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal totalMaturity = activeFds.stream()
                                .map(FixedDeposit::getMaturityAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                ProfileSnapshot snapshot = profileRepository.findById(userId)
                                .map(p -> ProfileSnapshot.builder()
                                                .persona(p.getPersona())
                                                .liquidityScore(p.getLiquidityScore())
                                                .maturitySpreadScore(p.getMaturitySpreadScore())
                                                .penaltyRisk(p.getPenaltyRisk())
                                                .concentrationRisk(p.getConcentrationRisk())
                                                .ladderScore(p.getLadderScore())
                                                .portfolioHealthScore(p.getPortfolioHealthScore())
                                                .recommendation(p.getRecommendation())
                                                .updatedAt(p.getUpdatedAt())
                                                .build())
                                .orElse(null);

                return PortfolioResponse.builder()
                                .user(toUserResponse(user))
                                .activeFds(activeFds.stream().map(this::toFdResponse).collect(Collectors.toList()))
                                .totalPrincipal(totalPrincipal)
                                .totalMaturityValue(totalMaturity)
                                .financialProfile(snapshot)
                                .build();
        }

        private void triggerAnalyticsRefresh(Long userId) {
                try {
                        User user = userRepository.findById(userId).orElseThrow();
                        List<FixedDeposit> activeFds = fdRepository.findActiveFdsByUserId(userId);

                        var riskResult = goRiskClient.analyze(user, activeFds);
                        var analyticsResult = fastApiClient.generate(user, activeFds, riskResult);

                        UserFinancialProfile profile = profileRepository.findById(userId)
                                        .orElse(UserFinancialProfile.builder().userId(userId).build());

                        profile.setPersona(analyticsResult.persona);
                        profile.setLiquidityScore(riskResult.liquidityScore);
                        profile.setMaturitySpreadScore(riskResult.maturitySpreadScore);
                        profile.setPenaltyRisk(riskResult.penaltyExposure);
                        profile.setConcentrationRisk(riskResult.concentrationRisk);
                        profile.setLadderScore(riskResult.ladderScore);
                        profile.setPortfolioHealthScore(analyticsResult.portfolioHealthScore);
                        profile.setRecommendation(analyticsResult.recommendation);
                        profileRepository.save(profile);

                        log.info("Analytics refreshed for userId={} persona={} health={}",
                                        userId, analyticsResult.persona, analyticsResult.portfolioHealthScore);
                } catch (Exception e) {
                        log.warn("Analytics refresh failed for userId={}: {}", userId, e.getMessage());
                }
        }

        public FdResponse toFdResponse(FixedDeposit fd) {
                return FdResponse.builder()
                                .id(fd.getId())
                                .userId(fd.getUser().getId())
                                .principal(fd.getPrincipal())
                                .interestRate(fd.getInterestRate())
                                .durationMonths(fd.getDurationMonths())
                                .maturityAmount(fd.getMaturityAmount())
                                .startDate(fd.getStartDate())
                                .maturityDate(fd.getMaturityDate())
                                .fdType(fd.getFdType())
                                .status(fd.getStatus())
                                .build();
        }

        public UserResponse toUserResponse(User user) {
                return UserResponse.builder()
                                .id(user.getId())
                                .name(user.getName())
                                .email(user.getEmail())
                                .monthlyIncome(user.getMonthlyIncome())
                                .monthlyExpenses(user.getMonthlyExpenses())
                                .createdAt(user.getCreatedAt())
                                .build();
        }
}