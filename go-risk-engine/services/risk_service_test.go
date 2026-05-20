package services

import (
	"fd-management/go-risk-engine/models"
	"testing"
	"time"
)

func assertExact(t *testing.T, expected, got float64) {
	t.Helper()
	if got != expected {
		t.Errorf("expected exactly %.2f, got %.2f", expected, got)
	}
}

var fixedNow = time.Date(2026, 5, 20, 0, 0, 0, 0, time.UTC)

func singleShortFd(principal float64) models.FdItem {
	return models.FdItem{ID: 1, Principal: principal, FdType: "SHORT_TERM",
		DurationMonths: 6, MaturityDate: "2026-06-01"}
}

func singleLongFd(principal float64) models.FdItem {
	return models.FdItem{ID: 1, Principal: principal, FdType: "LONG_TERM",
		DurationMonths: 36, MaturityDate: "2028-01-01"}
}


func Test_calcLiquidityScore_shouldReturn0_whenInputIsEmptyFdList(t *testing.T) {
	req := models.RiskRequest{MonthlyIncome: 80000, MonthlyExpenses: 40000, Fds: []models.FdItem{}}
	assertExact(t, 0, calcLiquidityScore(req))
}

func Test_calcLiquidityScore_shouldReturn100_whenInputIsSingleShortTermFdCovers6MonthsOfExpenses(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleShortFd(240000)},
	}
	assertExact(t, 100, calcLiquidityScore(req))
}

func Test_calcLiquidityScore_shouldReturn35_whenInputIsShortTermCovers1Point5MonthsAndSavingsRateIs50Pct(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleShortFd(60000)},
	}
	assertExact(t, 35, calcLiquidityScore(req))
}

func Test_calcLiquidityScore_shouldReturn60_whenInputIsShortTermPrincipalCovers3MonthsAndSavingsRateIsHalf(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleShortFd(120000)},
	}
	assertExact(t, 60, calcLiquidityScore(req))
}

func Test_calcLiquidityScore_shouldReturn10_whenInputIsOnlyLongTermFd(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleLongFd(500000)},
	}
	assertExact(t, 10, calcLiquidityScore(req))
}

func Test_calcLiquidityScore_shouldReturn100_whenInputIsFullCoverageAndHighSavingsRate(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 100000, MonthlyExpenses: 20000,
		Fds: []models.FdItem{singleShortFd(120000)},
	}
	assertExact(t, 100, calcLiquidityScore(req))
}


func Test_calcMaturitySpreadScore_shouldReturn20_whenInputIsExactlyOneFd(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{singleShortFd(100000)}}
	assertExact(t, 20, calcMaturitySpreadScore(req))
}

func Test_calcMaturitySpreadScore_shouldReturn0_whenInputIsThreeFdsMaturingOnIdenticalDate(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2026-06-01"},
	}}
	assertExact(t, 0, calcMaturitySpreadScore(req))
}

func Test_calcMaturitySpreadScore_shouldReturn100_whenInputIsFourFdsMaturingExactlyOneYearApart(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2025-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-01-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
	}}
	assertExact(t, 100, calcMaturitySpreadScore(req))
}

func Test_calcMaturitySpreadScore_shouldReturn86Point84_whenInputIsThreeFdsWithUnevenGaps(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-07-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-07-01"},
	}}
	assertExact(t, 86.84, calcMaturitySpreadScore(req))
}


func Test_calcPenaltyExposure_shouldReturn100_whenInputIsZeroMonthlyIncome(t *testing.T) {
	req := models.RiskRequest{MonthlyIncome: 0, MonthlyExpenses: 40000, Fds: []models.FdItem{}}
	assertExact(t, 100, calcPenaltyExposureAt(req, fixedNow))
}

func Test_calcPenaltyExposure_shouldReturn6_whenInputIsLowExpensesAndAlreadyMaturedShortTermFd(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 100000, MonthlyExpenses: 20000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 200000, FdType: "SHORT_TERM",
				DurationMonths: 3, MaturityDate: "2025-09-01"},
		},
	}
	assertExact(t, 6, calcPenaltyExposureAt(req, fixedNow))
}

func Test_calcPenaltyExposure_shouldReturn73Point58_whenInputIsHighExpensesAndLongTermFdLockedUntil2029(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 72000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 500000, FdType: "LONG_TERM",
				DurationMonths: 48, MaturityDate: "2029-01-01"},
		},
	}
	assertExact(t, 73.58, calcPenaltyExposureAt(req, fixedNow))
}

func Test_calcPenaltyExposure_shouldReturn66Point42_whenInputIsExpensesExceedingIncomeAndFdMaturingIn2028(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 10000, MonthlyExpenses: 99999,
		Fds: []models.FdItem{singleLongFd(500000)},
	}
	assertExact(t, 66.42, calcPenaltyExposureAt(req, fixedNow))
}


func Test_calcConcentrationRisk_shouldReturn0_whenInputIsSingleFdWithPrincipal500000(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{singleShortFd(500000)}}
	assertExact(t, 0, calcConcentrationRisk(req))
}

func Test_calcConcentrationRisk_shouldReturn75_whenInputIsFourFdsWithEqualPrincipal(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-07-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-01-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-07-01"},
	}}
	assertExact(t, 75, calcConcentrationRisk(req))
}
func Test_calcConcentrationRisk_shouldReturn37Point50_whenInputIsTwoUnequalFdsBothBelowDICGCLimit(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 300000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	assertExact(t, 37.50, calcConcentrationRisk(req))
}

func Test_calcConcentrationRisk_shouldReturn22Point78_whenInputIsOneFdExceedingDICGCLimitOf500000(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 1000000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	assertExact(t, 22.78, calcConcentrationRisk(req))
}

func Test_calcConcentrationRisk_shouldReturn44Point44_whenInputIsBothFdsBelowDICGCLimit(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 400000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	assertExact(t, 44.44, calcConcentrationRisk(req))
}

func Test_calcConcentrationRisk_shouldReturnLowerScore_whenInputIsFdBreachingDICGCLimitVsNoBreach(t *testing.T) {
	reqBreach := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 1000000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	reqNoBreach := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 400000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	breach := calcConcentrationRisk(reqBreach)
	noBreach := calcConcentrationRisk(reqNoBreach)
	if breach >= noBreach {
		t.Errorf("breach score (%.2f) must be strictly less than no-breach score (%.2f)", breach, noBreach)
	}
}


func Test_calcLadderScore_shouldReturn15_whenInputIsExactlyOneFd(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{singleLongFd(200000)}}
	assertExact(t, 15, calcLadderScore(req))
}

func Test_calcLadderScore_shouldReturn99Point97_whenInputIsFourFdsMaturingOneYearApartOverThreeYears(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2025-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-06-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-06-01"},
	}}
	assertExact(t, 99.97, calcLadderScore(req))
}

func Test_calcLadderScore_shouldReturn58_whenInputIsTwoFdsMaturingExactlyOneYearApart(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2025-06-01"},
		{ID: 2, Principal: 200000, FdType: "LONG_TERM", MaturityDate: "2026-06-01"},
	}}
	assertExact(t, 58, calcLadderScore(req))
}

func Test_calcLadderScore_shouldReturn67Point31_whenInputIsThreeFdsMaturingWithin29DaysOfEachOther(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-15"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2026-06-30"},
	}}
	assertExact(t, 67.31, calcLadderScore(req))
}

func Test_calcLadderScore_shouldReturnScoreNear100_whenInputIsSpanOfExactly4Years(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2025-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2029-01-01"},
	}}
	score := calcLadderScore(req)
	if score < 80 || score > 100 {
		t.Errorf("expected score near 100 for 4-year span, got %.2f", score)
	}
}

func Test_calcLadderScore_shouldReturnScoreBelow100_whenInputIsSpanExceeding5Years(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2022-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2024-01-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2026-01-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
	}}
	score := calcLadderScore(req)
	if score >= 100 {
		t.Errorf("expected score below 100 for 6-year span (decay branch), got %.2f", score)
	}
}


func Test_analyzeRisk_shouldReturnAllFiveScoresBetween0And100_whenInputIsRealisticPortfolio(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 200000, FdType: "SHORT_TERM",
				DurationMonths: 6, MaturityDate: "2026-01-01"},
			{ID: 2, Principal: 300000, FdType: "LONG_TERM",
				DurationMonths: 24, MaturityDate: "2027-01-01"},
			{ID: 3, Principal: 150000, FdType: "LONG_TERM",
				DurationMonths: 36, MaturityDate: "2028-01-01"},
		},
	}
	result := AnalyzeRisk(req)
	scores := map[string]float64{
		"LiquidityScore":      result.LiquidityScore,
		"MaturitySpreadScore": result.MaturitySpreadScore,
		"PenaltyExposure":     result.PenaltyExposure,
		"ConcentrationRisk":   result.ConcentrationRisk,
		"LadderScore":         result.LadderScore,
	}
	for name, score := range scores {
		if score < 0 || score > 100 {
			t.Errorf("%s out of range [0,100]: %.2f", name, score)
		}
	}
}

func Test_calcLiquidityScore_shouldReturn0_whenInputIsOnlyLongTermFdAndZeroIncome(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 0, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleLongFd(500000)},
	}
	assertExact(t, 0, calcLiquidityScore(req))
}

func Test_calcPenaltyExposure_shouldReturn49Point03_whenInputIsHighBurdenNoShortTermFdsAndFdMaturingSoon(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 72000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 500000, FdType: "LONG_TERM",
				DurationMonths: 2, MaturityDate: "2026-08-01"},
		},
	}
	assertExact(t, 49.03, calcPenaltyExposureAt(req, fixedNow))
}

func Test_calcPenaltyExposure_shouldReturn73Point00_whenInputIsLowBurdenAndVeryLongLockedFd(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 100000, MonthlyExpenses: 10000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 500000, FdType: "LONG_TERM",
				DurationMonths: 108, MaturityDate: "2035-01-01"},
		},
	}
	assertExact(t, 73.00, calcPenaltyExposureAt(req, fixedNow))
}

func Test_calcLadderScore_shouldReturn68_whenInputIsThreeFdsMaturingWithinSameMonth(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-11"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2026-06-21"},
	}}
	assertExact(t, 68, calcLadderScore(req))
}




func Test_calcConcentrationRisk_shouldReturn0_whenInputIsAllFdsWithZeroPrincipal(t *testing.T) {
    req := models.RiskRequest{Fds: []models.FdItem{
        {ID: 1, Principal: 0, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
        {ID: 2, Principal: 0, FdType: "LONG_TERM", MaturityDate: "2027-01-01"},
    }}
    assertExact(t, 0, calcConcentrationRisk(req))
}
// spanYears > 7 → default coverageScore=60 branch
func Test_calcLadderScore_shouldReturnScoreBelow80_whenInputIsSpanExceeding7Years(t *testing.T) {
    req := models.RiskRequest{Fds: []models.FdItem{
        {ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2020-01-01"},
        {ID: 2, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
    }}
    score := calcLadderScore(req)
    if score >= 80 {
        t.Errorf("expected score below 80 for >7yr span (default branch), got %.2f", score)
    }
}

func Test_calcMaturitySpreadScore_shouldReturn20_whenInputIsOneFdWithInvalidMaturityDate(t *testing.T) {
    req := models.RiskRequest{Fds: []models.FdItem{
        {ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "INVALID"},
        {ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "INVALID"},
    }}
    assertExact(t, 20, calcMaturitySpreadScore(req))
}

func Test_calcPenaltyExposure_shouldReturn35_whenInputIsInvalidMaturityDateFd(t *testing.T) {
    req := models.RiskRequest{
        MonthlyIncome: 80000, MonthlyExpenses: 40000,
        Fds: []models.FdItem{
            {ID: 1, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "INVALID"},
        },
    }
    assertExact(t, 35, calcPenaltyExposureAt(req, fixedNow))
}

func Test_calcLadderScore_shouldReturn15_whenInputIsAllFdsWithInvalidMaturityDate(t *testing.T) {
    req := models.RiskRequest{Fds: []models.FdItem{
        {ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "INVALID"},
        {ID: 2, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "INVALID"},
    }}
    assertExact(t, 15, calcLadderScore(req))
}

