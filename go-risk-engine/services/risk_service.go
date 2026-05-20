package services

import (
	"fd-management/go-risk-engine/models"
	"math"
	"sort"
	"time"
)

func AnalyzeRisk(req models.RiskRequest) models.RiskResult {
	liquidityCh := make(chan float64, 1)
	spreadCh := make(chan float64, 1)
	penaltyCh := make(chan float64, 1)
	concentrationCh := make(chan float64, 1)
	ladderCh := make(chan float64, 1)

	go func() { liquidityCh <- calcLiquidityScore(req) }()
	go func() { spreadCh <- calcMaturitySpreadScore(req) }()
	go func() { penaltyCh <- calcPenaltyExposure(req) }()
	go func() { concentrationCh <- calcConcentrationRisk(req) }()
	go func() { ladderCh <- calcLadderScore(req) }()

	return models.RiskResult{
		LiquidityScore:      <-liquidityCh,
		MaturitySpreadScore: <-spreadCh,
		PenaltyExposure:     <-penaltyCh,
		ConcentrationRisk:   <-concentrationCh,
		LadderScore:         <-ladderCh,

	}
}
func calcLiquidityScore(req models.RiskRequest) float64 {
	if len(req.Fds) == 0 || req.MonthlyExpenses == 0 {
		return 0
	}

	var shortTermPrincipal float64
	for _, fd := range req.Fds {
		if fd.FdType == "SHORT_TERM" {
			shortTermPrincipal += fd.Principal
		}
	}

	monthsCovered := shortTermPrincipal / req.MonthlyExpenses
	base := math.Min(monthsCovered/6.0*100, 100)

	savingsBonus := 0.0
	if req.MonthlyIncome > 0 {
		savingsRate := (req.MonthlyIncome - req.MonthlyExpenses) / req.MonthlyIncome
		savingsBonus = math.Min(savingsRate*20, 10)
	}

	return round(math.Min(base+savingsBonus, 100), 2)
}
func calcMaturitySpreadScore(req models.RiskRequest) float64 {
	if len(req.Fds) == 0 {
		return 0
	}
	if len(req.Fds) == 1 {
		return 20
	}

	dates := []time.Time{}
	for _, fd := range req.Fds {
		t, err := time.Parse("2006-01-02", fd.MaturityDate)
		if err != nil {
			continue
		}
		dates = append(dates, t)
	}
	if len(dates) < 2 {
		return 20
	}
	sort.Slice(dates, func(i, j int) bool { return dates[i].Before(dates[j]) })

	gaps := []float64{}
	for i := 1; i < len(dates); i++ {
		gap := dates[i].Sub(dates[i-1]).Hours() / 24
		gaps = append(gaps, gap)
	}

	mean := 0.0
	for _, g := range gaps {
		mean += g
	}
	mean /= float64(len(gaps))

	if mean == 0 {
		return 0
	}

	variance := 0.0
	for _, g := range gaps {
		diff := g - mean
		variance += diff * diff
	}
	variance /= float64(len(gaps))
	stdDev := math.Sqrt(variance)

	cv := stdDev / mean

	spreadScore := 100 - math.Min(cv/2.0*90, 90)

	countBonus := math.Min(float64(len(req.Fds)-2)*2, 10)

	return round(math.Min(spreadScore+countBonus, 100), 2)
}

func calcPenaltyExposure(req models.RiskRequest) float64 {
	if req.MonthlyIncome == 0 {
		return 100
	}

	expenseBurden := math.Min(req.MonthlyExpenses/req.MonthlyIncome, 1.0)

	var shortTermPrincipal float64
	for _, fd := range req.Fds {
		if fd.FdType == "SHORT_TERM" {
			shortTermPrincipal += fd.Principal
		}
	}
	liquidityGap := 0.0
	if req.MonthlyExpenses > 0 {
		monthsCovered := shortTermPrincipal / req.MonthlyExpenses
		if monthsCovered < 3 {
			liquidityGap = math.Max((3-monthsCovered)/3, 0)
		}
	}

	likelihood := expenseBurden*0.6 + liquidityGap*0.4
	severity := 0.0
	if len(req.Fds) > 0 {
		var totalPrincipal, weightedMonths float64
		now := time.Now()
		for _, fd := range req.Fds {
			maturity, err := time.Parse("2006-01-02", fd.MaturityDate)
			if err != nil {
				continue
			}
			monthsLeft := math.Max(maturity.Sub(now).Hours()/24/30, 0)
			weightedMonths += monthsLeft * fd.Principal
			totalPrincipal += fd.Principal
		}
		if totalPrincipal > 0 {
			avgMonthsLeft := weightedMonths / totalPrincipal
			severity = math.Min(avgMonthsLeft/60.0, 1.0)
		}
	}

	score := (likelihood*0.5 + severity*0.5) * 100
	return round(math.Min(score, 100), 2)
}

func calcConcentrationRisk(req models.RiskRequest) float64 {
	if len(req.Fds) == 0 {
		return 0
	}

	var totalPrincipal float64
	for _, fd := range req.Fds {
		totalPrincipal += fd.Principal
	}
	if totalPrincipal == 0 {
		return 0
	}

	hhi := 0.0
	dicgcBreachPenalty := 0.0
	for _, fd := range req.Fds {
		share := fd.Principal / totalPrincipal
		hhi += share * share

		if fd.Principal > 500000 {
			excess := (fd.Principal - 500000) / fd.Principal
			dicgcBreachPenalty += excess * 0.1
		}
	}

	diversificationScore := (1 - hhi) * 100

	finalScore := math.Max(diversificationScore-dicgcBreachPenalty*100, 0)
	return round(math.Min(finalScore, 100), 2)
}

func calcLadderScore(req models.RiskRequest) float64 {
	if len(req.Fds) == 0 {
		return 0
	}
	if len(req.Fds) == 1 {
		return 15
	}

	dates := []time.Time{}
	for _, fd := range req.Fds {
		t, err := time.Parse("2006-01-02", fd.MaturityDate)
		if err != nil {
			continue
		}
		dates = append(dates, t)
	}
	if len(dates) < 2 {
		return 15
	}
	sort.Slice(dates, func(i, j int) bool { return dates[i].Before(dates[j]) })

	spanDays := dates[len(dates)-1].Sub(dates[0]).Hours() / 24
	spanYears := spanDays / 365.0

	coverageScore := 0.0
	switch {
	case spanYears < 1:
		coverageScore = 20
	case spanYears < 3:
		coverageScore = 20 + (spanYears-1)/2*60
	case spanYears <= 5:
		coverageScore = 100
	case spanYears <= 7:
		coverageScore = 100 - (spanYears-5)/2*20
	default:
		coverageScore = 60
	}

	gaps := []float64{}
	for i := 1; i < len(dates); i++ {
		gaps = append(gaps, dates[i].Sub(dates[i-1]).Hours()/24)
	}
	mean := 0.0
	for _, g := range gaps {
		mean += g
	}
	mean /= float64(len(gaps))

	regularityScore := 100.0
	if mean > 0 {
		variance := 0.0
		for _, g := range gaps {
			d := g - mean
			variance += d * d
		}
		variance /= float64(len(gaps))
		cv := math.Sqrt(variance) / mean
		regularityScore = math.Max(100-cv*50, 10)
	}

	rungScore := 0.0
	n := len(req.Fds)
	switch {
	case n < 2:
		rungScore = 10
	case n == 2:
		rungScore = 50
	case n >= 3 && n <= 5:
		rungScore = 100
	case n <= 7:
		rungScore = 80
	default:
		rungScore = 60
	}

	score := coverageScore*0.40 + regularityScore*0.40 + rungScore*0.20
	return round(math.Min(score, 100), 2)
}

func round(val float64, places int) float64 {
	shift := math.Pow(10, float64(places))
	return math.Round(val*shift) / shift
}
