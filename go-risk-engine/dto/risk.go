package dto

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
	FdType         string  `json:"fdType"` 
}

type RiskResult struct {
	LiquidityScore      float64 `json:"liquidityScore"`
	MaturitySpreadScore float64 `json:"maturitySpreadScore"`
	PenaltyExposure     float64 `json:"penaltyExposure"`
	ConcentrationRisk   float64 `json:"concentrationRisk"`   
	LadderScore         float64 `json:"ladderScore"`          
}