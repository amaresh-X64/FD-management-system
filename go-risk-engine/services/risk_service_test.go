package services

import (
	"fd-management/go-risk-engine/models"
	"testing"
)

// ── Helpers ───────────────────────────────────────────────────────────────

func singleShortFd(principal float64) models.FdItem {
	return models.FdItem{ID: 1, Principal: principal, FdType: "SHORT_TERM",
		DurationMonths: 6, MaturityDate: "2026-06-01"}
}

func singleLongFd(principal float64) models.FdItem {
	return models.FdItem{ID: 1, Principal: principal, FdType: "LONG_TERM",
		DurationMonths: 36, MaturityDate: "2028-01-01"}
}

// ── 1. Liquidity Score ────────────────────────────────────────────────────

func TestLiquidity_NoFds(t *testing.T) {
	req := models.RiskRequest{MonthlyIncome: 80000, MonthlyExpenses: 40000, Fds: []models.FdItem{}}
	if calcLiquidityScore(req) != 0 {
		t.Errorf("expected 0 for empty FDs")
	}
}

func TestLiquidity_FullSixMonthCoverage(t *testing.T) {
	// 240000 short-term / 40000 expenses = 6 months = base 100
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleShortFd(240000)},
	}
	score := calcLiquidityScore(req)
	if score < 100 {
		t.Errorf("expected >= 100 for full 6-month coverage, got %.2f", score)
	}
}

func TestLiquidity_PartialCoverage(t *testing.T) {
	// 120000 / 40000 = 3 months = 50% of target → base 50
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleShortFd(120000)},
	}
	score := calcLiquidityScore(req)
	// base = 50 + savings bonus (0.5 savings rate * 20 = 10, capped at 10) = 60
	if score < 55 || score > 65 {
		t.Errorf("expected ~60 for 3-month coverage with good savings rate, got %.2f", score)
	}
}

func TestLiquidity_OnlyLongTermFds(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 40000,
		Fds: []models.FdItem{singleLongFd(500000)},
	}
	score := calcLiquidityScore(req)
	// Savings bonus only — no short-term principal
	if score > 15 {
		t.Errorf("expected low score with only long-term FDs, got %.2f", score)
	}
}

func TestLiquidity_HighSavingsRateBonus(t *testing.T) {
	// savings rate = (100000-20000)/100000 = 80% → bonus = min(0.8*20, 10) = 10
	req := models.RiskRequest{
		MonthlyIncome: 100000, MonthlyExpenses: 20000,
		Fds: []models.FdItem{singleShortFd(120000)}, // 6 months coverage = 100 base
	}
	score := calcLiquidityScore(req)
	if score != 100 {
		t.Errorf("expected 100 with full coverage + high savings, got %.2f", score)
	}
}

// ── 2. Maturity Spread ────────────────────────────────────────────────────

func TestSpread_SingleFd(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{singleShortFd(100000)}}
	if calcMaturitySpreadScore(req) != 20 {
		t.Errorf("expected 20 for single FD")
	}
}

func TestSpread_AllSameDate(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2026-06-01"},
	}}
	score := calcMaturitySpreadScore(req)
	if score > 15 {
		t.Errorf("expected near 0 for all same maturity date, got %.2f", score)
	}
}

func TestSpread_PerfectYearlyLadder(t *testing.T) {
	// 4 FDs each 1 year apart — perfect ladder
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2025-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-01-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
	}}
	score := calcMaturitySpreadScore(req)
	if score < 80 {
		t.Errorf("expected high spread score for perfect ladder, got %.2f", score)
	}
}

// ── 3. Penalty Exposure ───────────────────────────────────────────────────

func TestPenalty_ZeroIncome(t *testing.T) {
	req := models.RiskRequest{MonthlyIncome: 0, MonthlyExpenses: 40000, Fds: []models.FdItem{}}
	if calcPenaltyExposure(req) != 100 {
		t.Errorf("expected 100 for zero income")
	}
}

func TestPenalty_LowRisk(t *testing.T) {
	// Low expenses, FDs maturing soon → low penalty exposure
	req := models.RiskRequest{
		MonthlyIncome: 100000, MonthlyExpenses: 20000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 200000, FdType: "SHORT_TERM",
				DurationMonths: 3, MaturityDate: "2025-09-01"},
		},
	}
	score := calcPenaltyExposure(req)
	if score > 40 {
		t.Errorf("expected low penalty exposure for low expense ratio, got %.2f", score)
	}
}

func TestPenalty_HighRisk(t *testing.T) {
	// High expenses + FD locked for 4 more years
	req := models.RiskRequest{
		MonthlyIncome: 80000, MonthlyExpenses: 72000,
		Fds: []models.FdItem{
			{ID: 1, Principal: 500000, FdType: "LONG_TERM",
				DurationMonths: 48, MaturityDate: "2029-01-01"},
		},
	}
	score := calcPenaltyExposure(req)
	if score < 60 {
		t.Errorf("expected high penalty exposure, got %.2f", score)
	}
}

func TestPenalty_CapsAt100(t *testing.T) {
	req := models.RiskRequest{
		MonthlyIncome: 10000, MonthlyExpenses: 99999,
		Fds: []models.FdItem{singleLongFd(500000)},
	}
	if calcPenaltyExposure(req) > 100 {
		t.Errorf("penalty exposure must not exceed 100")
	}
}

// ── 4. Concentration Risk (HHI) ───────────────────────────────────────────

func TestConcentration_SingleFd(t *testing.T) {
	// HHI = 1.0 → score = 0 (worst concentration)
	req := models.RiskRequest{Fds: []models.FdItem{singleShortFd(500000)}}
	score := calcConcentrationRisk(req)
	if score != 0 {
		t.Errorf("expected 0 for single FD (HHI=1), got %.2f", score)
	}
}

func TestConcentration_EqualDistribution(t *testing.T) {
	// 4 equal FDs → HHI = 4*(0.25^2) = 0.25 → score = 75
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-07-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-01-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-07-01"},
	}}
	score := calcConcentrationRisk(req)
	if score < 70 || score > 80 {
		t.Errorf("expected ~75 for equal 4-FD distribution, got %.2f", score)
	}
}

func TestConcentration_DICGCBreachPenalty(t *testing.T) {
	// FD over ₹5 lakh should be penalised
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 1000000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	scoreWithBreach := calcConcentrationRisk(req)

	req2 := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 400000, FdType: "LONG_TERM", MaturityDate: "2028-01-01"},
		{ID: 2, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2026-01-01"},
	}}
	scoreWithout := calcConcentrationRisk(req2)

	if scoreWithBreach >= scoreWithout {
		t.Errorf("DICGC breach should reduce score: breach=%.2f, no-breach=%.2f",
			scoreWithBreach, scoreWithout)
	}
}

// ── 5. Ladder Score ───────────────────────────────────────────────────────

func TestLadder_SingleFd(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{singleLongFd(200000)}}
	if calcLadderScore(req) != 15 {
		t.Errorf("expected 15 for single FD")
	}
}

func TestLadder_IdealThreeToFiveRungs(t *testing.T) {
	// 4 FDs, 1 year apart, 4-year span = ideal ladder
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2025-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2027-06-01"},
		{ID: 4, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2028-06-01"},
	}}
	score := calcLadderScore(req)
	if score < 85 {
		t.Errorf("expected high ladder score for ideal 4-rung yearly ladder, got %.2f", score)
	}
}

func TestLadder_TooFewRungs(t *testing.T) {
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 200000, FdType: "SHORT_TERM", MaturityDate: "2025-06-01"},
		{ID: 2, Principal: 200000, FdType: "LONG_TERM", MaturityDate: "2026-06-01"},
	}}
	score := calcLadderScore(req)
	if score > 75 {
		t.Errorf("expected moderate score for 2-rung ladder, got %.2f", score)
	}
}

func TestLadder_AllClusteredTogether(t *testing.T) {
	// All maturities within same month — regularity is fine but coverage span is tiny
	// 3 FDs = good rung count bonus, but ~0 coverage span → overall moderate-low score
	req := models.RiskRequest{Fds: []models.FdItem{
		{ID: 1, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-01"},
		{ID: 2, Principal: 100000, FdType: "SHORT_TERM", MaturityDate: "2026-06-15"},
		{ID: 3, Principal: 100000, FdType: "LONG_TERM", MaturityDate: "2026-06-30"},
	}}
	score := calcLadderScore(req)
	// Clustered FDs: span < 1 month → coverage score = 20, rung bonus applies
	// Score should be well below a proper ladder (which scores 85+)
	if score > 75 {
		t.Errorf("clustered maturities should score significantly below a proper ladder, got %.2f", score)
	}
}

// ── 6. Full AnalyzeRisk concurrent test ──────────────────────────────────

func TestAnalyzeRisk_AllScoresInRange(t *testing.T) {
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
