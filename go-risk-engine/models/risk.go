package models

// RiskRequest is the payload sent by Spring Boot
type RiskRequest struct {
	MonthlyIncome   float64  `json:"monthlyIncome"`
	MonthlyExpenses float64  `json:"monthlyExpenses"`
	Fds             []FdItem `json:"fds"`
}

type FdItem struct {
	ID             int64   `json:"id"`
	Principal      float64 `json:"principal"`
	InterestRate   float64 `json:"interestRate"`
	DurationMonths int     `json:"durationMonths"`
	MaturityDate   string  `json:"maturityDate"`
	FdType         string  `json:"fdType"` // SHORT_TERM or LONG_TERM
}

// RiskResult is returned to Spring Boot
// Now includes 5 scores instead of 3
type RiskResult struct {
	LiquidityScore      float64 `json:"liquidityScore"`
	MaturitySpreadScore float64 `json:"maturitySpreadScore"`
	PenaltyExposure     float64 `json:"penaltyExposure"`
	ConcentrationRisk   float64 `json:"concentrationRisk"`   // NEW: HHI-based
	LadderScore         float64 `json:"ladderScore"`          // NEW: laddering quality
}