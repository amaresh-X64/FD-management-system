from fastapi import APIRouter
from models.schemas import AnalyticsRequest, AnalyticsResponse
from analytics.portfolio_analytics import generate_analytics

router = APIRouter()

@router.post("/analytics/generate", response_model=AnalyticsResponse)
def generate(req: AnalyticsRequest):
    return generate_analytics(req)
