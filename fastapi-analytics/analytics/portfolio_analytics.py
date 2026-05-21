from models.schemas import AnalyticsRequest, AnalyticsResponse
import pandas as pd

def generate_analytics(req: AnalyticsRequest) -> AnalyticsResponse:
    persona      = _assign_persona(req)
    recommendation = _generate_recommendation(req)
    health_score = _calc_health_score(req)

    return AnalyticsResponse(
        persona=persona,
        portfolioHealthScore=round(health_score, 2),
        recommendation=recommendation
    )

PERSONA_VECTORS: dict[str, dict[str, float]] = {
    "Conservative Saver": {"liq": 80, "spread": 70, "penalty": 20, "conc": 70, "ladder": 75},
    "Liquidity Risk User": {"liq": 15, "spread": 30, "penalty": 80, "conc": 40, "ladder": 25},
    "Long-Term Planner":  {"liq": 40, "spread": 60, "penalty": 40, "conc": 65, "ladder": 70},
    "Aggressive Reinvestor": {"liq": 20, "spread": 40, "penalty": 55, "conc": 30, "ladder": 35},
    "Balanced Investor":  {"liq": 60, "spread": 60, "penalty": 35, "conc": 65, "ladder": 65},
}

def euclidean_distance(a: dict[str, float], b: dict[str, float]) -> float:
    return sum((a[k] - b[k]) ** 2 for k in b) ** 0.5

def nearest_persona(profile: dict[str, float], vectors: dict = PERSONA_VECTORS) -> str:
    return min(vectors, key=lambda name: euclidean_distance(profile, vectors[name]))

def _assign_persona(req: AnalyticsRequest) -> str:
    if req.penaltyExposure > 70:
        return "Liquidity Risk User"
    long_ratio = sum(1 for fd in req.fds if fd.fdType == "LONG_TERM") / len(req.fds) if req.fds else 0
    if long_ratio >= 0.8 and req.ladderScore > 65:
        return "Long-Term Planner"
    profile = {"liq": req.liquidityScore, "spread": req.maturitySpreadScore,
               "penalty": req.penaltyExposure, "conc": req.concentrationRisk, "ladder": req.ladderScore}
    return nearest_persona(profile)


def _generate_recommendation(req: AnalyticsRequest) -> str:
    tips = []

    monthly_expenses = req.monthlyExpenses
    short_principal = 0

    if req.fds:
        df = pd.DataFrame([{
            "id": fd.id,
            "principal": fd.principal,
            "maturityAmount": fd.maturityAmount,
            "durationMonths": fd.durationMonths,
            "maturityDate": pd.to_datetime(fd.maturityDate),
            "fdType": fd.fdType
        } for fd in req.fds])

        total_principal = df["principal"].sum()
        short_term_df = df[df["fdType"] == "SHORT_TERM"]
        long_term_df  = df[df["fdType"] == "LONG_TERM"]
        short_principal = short_term_df["principal"].sum() if not short_term_df.empty else 0

    if req.liquidityScore < 50:
        target = monthly_expenses * 6
        shortfall = max(target - short_principal, 0)
        if shortfall > 0:
            tips.append(
                f"Your emergency fund is below the 6-month safety benchmark. "
                f"Consider adding ₹{shortfall:,.0f} in short-term FDs (≤12 months) "
                f"to reach the recommended ₹{target:,.0f} buffer."
            )

    if req.ladderScore < 50:
        if len(req.fds) < 3:
            tips.append(
                f"You only have {len(req.fds)} FD(s). Industry best practice recommends "
                f"3–5 FDs with maturities staggered 12 months apart (laddering), "
                f"so one FD matures every year without breaking any prematurely."
            )
        else:
            tips.append(
                "Your FDs mature too close together. Spread them across different years "
                "— ideally one maturing per year — to maintain steady liquidity "
                "and capture varying interest rates at renewal."
            )

    elif req.maturitySpreadScore < 40 and len(req.fds) >= 3:
        tips.append(
            "Your FD maturities are clustered. When they all mature together, "
            "you face reinvestment risk — you may be forced to renew at lower rates. "
            "Stagger maturities across different quarters."
        )

    if req.penaltyExposure > 60:
        expense_ratio = req.monthlyExpenses / req.monthlyIncome if req.monthlyIncome > 0 else 0
        tips.append(
            f"Your expense-to-income ratio is {expense_ratio*100:.0f}%, which means "
            f"a high chance of needing to break FDs early. "
            f"Keep at least {monthly_expenses * 3:,.0f} (3 months expenses) "
            f"in liquid or short-term instruments before locking more in long-term FDs."
        )

    if req.concentrationRisk < 40 and req.fds:
        large_fds = [fd for fd in req.fds if fd.principal > 500000]
        if large_fds:
            tips.append(
                f"You have {len(large_fds)} FD(s) above ₹5 lakh. DICGC insures only "
                f"₹5 lakh per depositor per bank. Consider splitting large FDs across "
                f"different banks to maximise insurance coverage."
            )
        else:
            tips.append(
                "Your portfolio is heavily concentrated in one or two FDs. "
                "Splitting into smaller FDs across different banks reduces risk "
                "and improves DICGC insurance coverage."
            )

    if req.fds:
        very_long = df[df["durationMonths"] > 48]
        if not very_long.empty:
            locked = very_long["principal"].sum()
            tips.append(
                f"₹{locked:,.0f} is locked in FDs with tenure > 4 years. "
                f"Consider whether you'll need this capital before maturity — "
                f"premature withdrawal incurs a 1% penalty plus interest loss."
            )

    if not tips:
        health = _calc_health_score(req)
        tips.append(
            f"Your portfolio health score is {health:.0f}/100 — looking strong! "
            f"Maintain your current laddering strategy and review FD rates at each "
            f"renewal to capture the best available rates."
        )

    return " | ".join(tips)


def select_weights(expense_ratio: float, fd_count: int) -> dict[str, float]:
    if fd_count <= 2:
        return {"liq": 0.25, "spread": 0.25, "ladder": 0.25, "conc": 0.15, "penalty": 0.10}
    elif expense_ratio > 0.6:
        return {"liq": 0.40, "spread": 0.15, "ladder": 0.15, "conc": 0.15, "penalty": 0.15}
    else:
        return {"liq": 0.30, "spread": 0.20, "ladder": 0.20, "conc": 0.15, "penalty": 0.15}

def _calc_health_score(req: AnalyticsRequest) -> float:
    expense_ratio = req.monthlyExpenses / req.monthlyIncome if req.monthlyIncome > 0 else 0.5
    w = select_weights(expense_ratio, len(req.fds))

    penalty_health = 100 - req.penaltyExposure
    score = (
        req.liquidityScore       * w["liq"]     +
        req.maturitySpreadScore  * w["spread"]   +
        req.ladderScore          * w["ladder"]   +
        req.concentrationRisk    * w["conc"]     +
        penalty_health           * w["penalty"]
    )
    return min(max(score, 0), 100)