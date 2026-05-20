package services

import (
	"fd-management/go-risk-engine/models"
	"testing"
)

func assertExact(t *testing.T, expected, got float64) {
	t.Helper()
	if got != expected {
		t.Errorf("expected exactly %.2f, got %.2f", expected, got)
	}
}

func singleShortFd(principal float64) models.FdItem {
	return models.FdItem{ID: 1, Principal: principal, FdType: "SHORT_TERM",
		DurationMonths: 6, MaturityDate: "2026-06-01"}
}

func singleLongFd(principal float64) models.FdItem {
	return models.FdItem{ID: 1, Principal: principal, FdType: "LONG_TERM",
		DurationMonths: 36, MaturityDate: "2028-01-01"}
}

func TestShouldReturn0WhenEmptyFdListIsGivenAsInputToLiquidityScore(t *testing.T) {
	req := models.RiskRequest{MonthlyIncome: 80000, MonthlyExpenses: 40000, Fds: []models.FdItem{}}
	assertExact(t, 0, calcLiquidityScore(req))
}

func TestShouldReturn100WhenSingleShortTermFdCovers6MonthsOfExpensesAsInputToLiquidityScore(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleShortFd(240000)},
	}
	assertExact(t, 100, calcLiquidityScore(req))
}

func TestShouldReturn60WhenShortTermPrincipalCovers3MonthsAndSavingsRateIsHalfAsInputToLiquidityScore(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleShortFd(120000)},
	}
	assertExact(t, 60, calcLiquidityScore(req))
}

func TestShouldReturn10WhenOnlyLongTermFdExistsAsInputToLiquidityScore(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleLongFd(500000)},
	}
	assertExact(t, 10, calcLiquidityScore(req))
}

func TestShouldReturn100WhenCoverageIsFullAndSavingsRateIsHighAsInputToLiquidityScore(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 100000, MonthlyExpenses: 20000,
		Fds: []models.FdItem{singleShortFd(120000)},
	}
	assertExact(t, 100, calcLiquidityScore(req))
}

func TestShouldReturn20WhenExactlyOneFdIsGivenAsInputToMaturitySpreadScore(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{singleShortFd(100000)}}
	assertExact(t, 20, calcMaturitySpreadScore(req))
}

func TestShouldReturn0WhenAllThreeFdsMatureOnIdenticalDateAsInputToMaturitySpreadScore(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2026-06-01"},
	}}
	assertExact(t, 0, calcMaturitySpreadScore(req))
}

func TestShouldReturn100WhenFourFdsMatureExactlyOneYearApartAsInputToMaturitySpreadScore(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2025-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-01-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
	}}
	assertExact(t, 100, calcMaturitySpreadScore(req))
}

func TestShouldReturn100WhenMonthlyIncomeIsZeroAsInputToPenaltyExposure(t *testing.T) {
	req := models.RiskRequest{MonthlyIncome: 0, MonthlyExpenses: 40000, Fds: []models.FdItem{}}
	assertExact(t, 100, calcPenaltyExposure(req))
}

func TestShouldReturn6WhenExpensesAreLowAndShortTermFdHasAlreadyMaturedAsInputToPenaltyExposure(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 100000, MonthlyExpenses: 20000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 200000, FdType: "SHORT_TERM",
				DurationMonths: 3, MaturityDate: "2025-09-01"},
		},
	}
	assertExact(t, 6, calcPenaltyExposure(req))
}

func TestShouldReturn73Point57WhenExpensesAreHighAndLongTermFdLockedUntil2029AsInputToPenaltyExposure(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 72000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 500000, FdType: "LONG_TERM",
				DurationMonths: 48, MaturityDate: "2029-01-01"},
		},
	}
	assertExact(t, 73.57, calcPenaltyExposure(req))
}

func TestShouldReturn66Point40WhenExpensesExceedIncomeAndFdMaturesin2028AsInputToPenaltyExposure(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 10000, MonthlyExpenses: 99999,
		Fds: []models.FdItem{singleLongFd(500000)},
	}
	assertExact(t, 66.40, calcPenaltyExposure(req))
}

func TestShouldReturn0WhenSingleFdWithPrincipal500000IsGivenAsInputToConcentrationRisk(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{singleShortFd(500000)}}
	assertExact(t, 0, calcConcentrationRisk(req))
}

func TestShouldReturn75WhenFourFdsWithEqualPrincipalAreGivenAsInputToConcentrationRisk(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-07-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-01-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-07-01"},
	}}
	assertExact(t, 75, calcConcentrationRisk(req))
}

func TestShouldReturn22Point78WhenOneFdExceedsDICGCLimitOf500000AsInputToConcentrationRisk(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 1000000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	assertExact(t, 22.78, calcConcentrationRisk(req))
}

func TestShouldReturn44Point44WhenBothFdsAreBelowDICGCLimitAsInputToConcentrationRisk(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 400000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	assertExact(t, 44.44, calcConcentrationRisk(req))
}

func TestShouldScoreLowerWhenFdBreachesDICGCLimitThanWhenItDoesNotAsInputToConcentrationRisk(t *testing.T) {
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

func TestShouldReturn15WhenExactlyOneFdIsGivenAsInputToLadderScore(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{singleLongFd(200000)}}
	assertExact(t, 15, calcLadderScore(req))
}

func TestShouldReturn99Point97WhenFourFdsMatureOneYearApartOverThreeYearsAsInputToLadderScore(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2025-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-06-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-06-01"},
	}}
	assertExact(t, 99.97, calcLadderScore(req))
}

func TestShouldReturn58WhenTwoFdsMatureExactlyOneYearApartAsInputToLadderScore(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2025-06-01"},
		{ID: 2, Principal: 200000, FdType: "LONG_TERM", MaturityDate: "2026-06-01"},
	}}
	assertExact(t, 58, calcLadderScore(req))
}

func TestShouldReturn67Point31WhenThreeFdsMatureWithin29DaysOfEachOtherAsInputToLadderScore(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-15"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2026-06-30"},
	}}
	assertExact(t, 67.31, calcLadderScore(req))
}

func TestShouldReturnAllFiveScoresBetween0And100WhenRealisticPortfolioIsGivenAsInputToAnalyzeRisk(t *testing.T) {
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
