import pytest
from analytics.portfolio_analytics import (
    generate_analytics,
    _assign_persona,
    _calc_health_score,
    _generate_recommendation,
)
from models.schemas import AnalyticsRequest, FdItem



def make_request(
    liquidity=70.0,
    spread=60.0,
    penalty=20.0,
    concentration=65.0,
    ladder=65.0,
    income=80000,
    expenses=40000,
    fds=None,
):
    if fds is None:
        fds = [
            FdItem(id=1, principal=200000, maturityAmount=230000,
                   durationMonths=12, maturityDate="2026-06-01", fdType="SHORT_TERM")
        ]
    return AnalyticsRequest(
        userId=1,
        monthlyIncome=income,
        monthlyExpenses=expenses,
        liquidityScore=liquidity,
        maturitySpreadScore=spread,
        penaltyExposure=penalty,
        concentrationRisk=concentration,
        ladderScore=ladder,
        fds=fds,
    )



def test_persona_conservative_saver():
    req = make_request(liquidity=85, spread=75, penalty=15, concentration=75, ladder=80)
    assert _assign_persona(req) == "Conservative Saver"


def test_persona_liquidity_risk_user():
    req = make_request(liquidity=20, spread=30, penalty=75, concentration=40, ladder=25)
    assert _assign_persona(req) == "Liquidity Risk User"


def test_persona_liquidity_risk_override():
    req = make_request(liquidity=80, spread=80, penalty=72, concentration=80, ladder=80)
    assert _assign_persona(req) == "Liquidity Risk User"


def test_persona_long_term_planner():
    fds = [
        FdItem(id=1, principal=200000, maturityAmount=250000,
               durationMonths=36, maturityDate="2027-01-01", fdType="LONG_TERM"),
        FdItem(id=2, principal=300000, maturityAmount=380000,
               durationMonths=48, maturityDate="2028-01-01", fdType="LONG_TERM"),
        FdItem(id=3, principal=100000, maturityAmount=120000,
               durationMonths=24, maturityDate="2026-06-01", fdType="LONG_TERM"),
        FdItem(id=4, principal=50000,  maturityAmount=55000,
               durationMonths=6,  maturityDate="2025-12-01", fdType="SHORT_TERM"),
    ]
    req = make_request(liquidity=30, spread=60, penalty=35, concentration=65, ladder=70, fds=fds)
    result = _assign_persona(req)
    assert result in ("Long-Term Planner", "Balanced Investor")


def test_persona_long_term_planner_override():
    fds = [
        FdItem(id=1, principal=300000, maturityAmount=380000,
               durationMonths=36, maturityDate="2027-01-01", fdType="LONG_TERM"),
        FdItem(id=2, principal=300000, maturityAmount=380000,
               durationMonths=48, maturityDate="2028-01-01", fdType="LONG_TERM"),
        FdItem(id=3, principal=300000, maturityAmount=380000,
               durationMonths=60, maturityDate="2029-01-01", fdType="LONG_TERM"),
    ]
    req = make_request(liquidity=30, spread=60, penalty=35, concentration=65, ladder=70, fds=fds)
    assert _assign_persona(req) == "Long-Term Planner"


def test_persona_aggressive_reinvestor():
    fds = [
        FdItem(id=1, principal=500000, maturityAmount=600000,
               durationMonths=36, maturityDate="2028-01-01", fdType="LONG_TERM"),
        FdItem(id=2, principal=500000, maturityAmount=600000,
               durationMonths=36, maturityDate="2028-06-01", fdType="LONG_TERM"),
    ]
    req = make_request(liquidity=20, spread=40, penalty=50, concentration=30, ladder=30, fds=fds)
    assert _assign_persona(req) == "Aggressive Reinvestor"


def test_persona_balanced_investor():
    req = make_request(liquidity=60, spread=60, penalty=30, concentration=65, ladder=65)
    assert _assign_persona(req) == "Balanced Investor"


def test_health_score_single_fd_weights():
    req = make_request(liquidity=80, spread=60, penalty=20, concentration=65, ladder=65)
    score = _calc_health_score(req)
    expected = 80*0.25 + 60*0.25 + 65*0.25 + 65*0.15 + 80*0.10
    assert score == pytest.approx(expected, abs=0.5)


def test_health_score_high_expense_weights():
    fds = [
        FdItem(id=1, principal=200000, maturityAmount=230000,
               durationMonths=12, maturityDate="2026-06-01", fdType="SHORT_TERM"),
        FdItem(id=2, principal=200000, maturityAmount=260000,
               durationMonths=24, maturityDate="2027-06-01", fdType="LONG_TERM"),
        FdItem(id=3, principal=200000, maturityAmount=290000,
               durationMonths=36, maturityDate="2028-06-01", fdType="LONG_TERM"),
    ]
    req = make_request(liquidity=80, spread=60, penalty=20,
                       concentration=65, ladder=65,
                       income=80000, expenses=55000, fds=fds)
    score = _calc_health_score(req)
    expected = 80*0.40 + 60*0.15 + 65*0.15 + 65*0.15 + 80*0.15
    assert score == pytest.approx(expected, abs=0.5)


def test_health_score_never_exceeds_100():
    req = make_request(liquidity=100, spread=100, penalty=0, concentration=100, ladder=100)
    assert _calc_health_score(req) <= 100.0


def test_health_score_never_below_0():
    req = make_request(liquidity=0, spread=0, penalty=100, concentration=0, ladder=0)
    assert _calc_health_score(req) >= 0.0


def test_health_score_better_portfolio_scores_higher():
    good = make_request(liquidity=90, spread=90, penalty=10, concentration=90, ladder=90)
    bad  = make_request(liquidity=10, spread=10, penalty=90, concentration=10, ladder=10)
    assert _calc_health_score(good) > _calc_health_score(bad)


def test_recommendation_low_liquidity_tip():
    req = make_request(liquidity=20, spread=70, penalty=20, ladder=70)
    rec = _generate_recommendation(req)
    assert "emergency fund" in rec.lower() or "short-term" in rec.lower()


def test_recommendation_low_ladder_tip():
    req = make_request(liquidity=70, spread=70, penalty=20, ladder=20)
    rec = _generate_recommendation(req)
    assert "ladder" in rec.lower() or "rung" in rec.lower() or "stagger" in rec.lower()


def test_recommendation_low_spread_tip():
    fds = [
        FdItem(id=1, principal=100000, maturityAmount=115000,
               durationMonths=12, maturityDate="2026-01-01", fdType="SHORT_TERM"),
        FdItem(id=2, principal=100000, maturityAmount=130000,
               durationMonths=24, maturityDate="2026-03-01", fdType="LONG_TERM"),
        FdItem(id=3, principal=100000, maturityAmount=145000,
               durationMonths=36, maturityDate="2026-06-01", fdType="LONG_TERM"),
    ]
    req = make_request(liquidity=70, spread=20, penalty=20, ladder=60, fds=fds)
    rec = _generate_recommendation(req)
    assert "cluster" in rec.lower() or "quarter" in rec.lower() or "reinvestment" in rec.lower()


def test_recommendation_high_penalty_tip():
    req = make_request(liquidity=70, spread=70, penalty=75, ladder=70)
    rec = _generate_recommendation(req)
    assert "expense" in rec.lower() or "break" in rec.lower() or "liquid" in rec.lower()


def test_recommendation_large_fd_concentration_tip():
    fds = [
        FdItem(id=1, principal=600000, maturityAmount=700000,
               durationMonths=24, maturityDate="2027-01-01", fdType="LONG_TERM"),
    ]
    req = make_request(liquidity=70, spread=70, penalty=20,
                       concentration=20, ladder=70, fds=fds)
    rec = _generate_recommendation(req)
    assert "5 lakh" in rec.lower() or "dicgc" in rec.lower() or "concentrated" in rec.lower()


def test_recommendation_healthy_portfolio():
    req = make_request(liquidity=85, spread=85, penalty=10, concentration=85, ladder=85)
    rec = _generate_recommendation(req)
    assert "strong" in rec.lower() or "health score" in rec.lower()


def test_generate_analytics_returns_all_fields():
    req = make_request(liquidity=70, spread=60, penalty=25, concentration=65, ladder=65)
    result = generate_analytics(req)
    assert result.persona is not None and len(result.persona) > 0
    assert 0 <= result.portfolioHealthScore <= 100
    assert result.recommendation is not None and len(result.recommendation) > 0


def test_generate_analytics_good_beats_bad():
    good = make_request(liquidity=90, spread=90, penalty=10, concentration=90, ladder=90)
    bad  = make_request(liquidity=10, spread=10, penalty=90, concentration=10, ladder=10)
    assert generate_analytics(good).portfolioHealthScore > generate_analytics(bad).portfolioHealthScore


def test_generate_analytics_persona_not_empty():
    req = make_request()
    result = generate_analytics(req)
    valid_personas = {
        "Conservative Saver", "Liquidity Risk User",
        "Long-Term Planner", "Aggressive Reinvestor", "Balanced Investor"
    }
    assert result.persona in valid_personas