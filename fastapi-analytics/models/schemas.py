from pydantic import BaseModel
from typing import List
from decimal import Decimal

class FdItem(BaseModel):
    id: int
    principal: float
    maturityAmount: float
    durationMonths: int
    maturityDate: str
    fdType: str

class AnalyticsRequest(BaseModel):
    userId: int
    monthlyIncome: float
    monthlyExpenses: float
    liquidityScore: float
    maturitySpreadScore: float
    penaltyExposure: float
    concentrationRisk: float = 50.0
    ladderScore: float = 50.0
    fds: List[FdItem]

class AnalyticsResponse(BaseModel):
    persona: str
    portfolioHealthScore: float
    recommendation: str