import pytest
from analytics.portfolio_analytics import (
    generate_analytics,
    _assign_persona,
    _calc_health_score,
    _generate_recommendation,
    PERSONA_VECTORS,
    euclidean_distance,
    nearest_persona,
    select_weights,
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
            FdItem(
                id=1,
                principal=200000,
                maturityAmount=230000,
                durationMonths=12,
                maturityDate="2026-06-01",
                fdType="SHORT_TERM",
            )
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


def make_fd(
    fd_id,
    principal,
    maturity_amount,
    duration_months,
    maturity_date,
    fd_type,
):
    return FdItem(
        id=fd_id,
        principal=principal,
        maturityAmount=maturity_amount,
        durationMonths=duration_months,
        maturityDate=maturity_date,
        fdType=fd_type,
    )


def test_persona_vectors_should_match_conservative_saver_baseline_when_loaded():
    assert PERSONA_VECTORS["Conservative Saver"] == {
        "liq": 80,
        "spread": 70,
        "penalty": 20,
        "conc": 70,
        "ladder": 75,
    }


def test_persona_vectors_should_match_liquidity_risk_user_baseline_when_loaded():
    assert PERSONA_VECTORS["Liquidity Risk User"] == {
        "liq": 15,
        "spread": 30,
        "penalty": 80,
        "conc": 40,
        "ladder": 25,
    }


def test_persona_vectors_should_match_long_term_planner_baseline_when_loaded():
    assert PERSONA_VECTORS["Long-Term Planner"] == {
        "liq": 40,
        "spread": 60,
        "penalty": 40,
        "conc": 65,
        "ladder": 70,
    }


def test_persona_vectors_should_match_aggressive_reinvestor_baseline_when_loaded():
    assert PERSONA_VECTORS["Aggressive Reinvestor"] == {
        "liq": 20,
        "spread": 40,
        "penalty": 55,
        "conc": 30,
        "ladder": 35,
    }


def test_persona_vectors_should_match_balanced_investor_baseline_when_loaded():
    assert PERSONA_VECTORS["Balanced Investor"] == {
        "liq": 60,
        "spread": 60,
        "penalty": 35,
        "conc": 65,
        "ladder": 65,
    }


def test_persona_vectors_should_contain_exactly_five_personas_when_loaded():
    assert len(PERSONA_VECTORS) == 5


def test_euclidean_distance_should_return_zero_when_profiles_are_identical():
    a = {"liq": 60, "spread": 60, "penalty": 35, "conc": 65, "ladder": 65}
    assert euclidean_distance(a, a) == pytest.approx(0.0)


def test_euclidean_distance_should_return_five_when_single_axis_difference_is_five():
    a = {"liq": 80, "spread": 70, "penalty": 20, "conc": 70, "ladder": 75}
    b = {"liq": 75, "spread": 70, "penalty": 20, "conc": 70, "ladder": 75}
    assert euclidean_distance(a, b) == pytest.approx(5.0)


def test_euclidean_distance_should_return_five_when_points_form_three_four_five_triangle():
    a = {"liq": 3, "spread": 4, "penalty": 0, "conc": 0, "ladder": 0}
    b = {"liq": 0, "spread": 0, "penalty": 0, "conc": 0, "ladder": 0}
    assert euclidean_distance(a, b) == pytest.approx(5.0)


def test_euclidean_distance_should_return_same_value_when_argument_order_is_swapped():
    a = {"liq": 80, "spread": 70, "penalty": 20, "conc": 70, "ladder": 75}
    b = {"liq": 60, "spread": 60, "penalty": 35, "conc": 65, "ladder": 65}
    assert euclidean_distance(a, b) == pytest.approx(euclidean_distance(b, a))


def test_nearest_persona_should_return_conservative_saver_when_profile_exactly_matches_conservative_vector():
    profile = {"liq": 80, "spread": 70, "penalty": 20, "conc": 70, "ladder": 75}
    assert nearest_persona(profile) == "Conservative Saver"


def test_nearest_persona_should_return_balanced_investor_when_profile_exactly_matches_balanced_vector():
    profile = {"liq": 60, "spread": 60, "penalty": 35, "conc": 65, "ladder": 65}
    assert nearest_persona(profile) == "Balanced Investor"


def test_nearest_persona_should_return_persona_with_minimum_distance_when_profile_is_given():
    profile = {"liq": 50, "spread": 55, "penalty": 30, "conc": 60, "ladder": 60}
    winner = nearest_persona(profile)
    winner_distance = euclidean_distance(profile, PERSONA_VECTORS[winner])

    for name, vector in PERSONA_VECTORS.items():
        assert winner_distance <= euclidean_distance(profile, vector)


def test_nearest_persona_should_return_different_winner_when_vectors_are_changed_for_boundary_profile():
    profile = {"liq": 72, "spread": 64, "penalty": 29, "conc": 67, "ladder": 69}

    original_winner = nearest_persona(profile)

    modified_vectors = {
        **PERSONA_VECTORS,
        "Balanced Investor": {
            **PERSONA_VECTORS["Balanced Investor"],
            "liq": 20,
            "spread": 20,
            "penalty": 80,
            "conc": 20,
            "ladder": 20,
        },
    }

    modified_winner = nearest_persona(profile, modified_vectors)

    assert original_winner == "Balanced Investor"
    assert modified_winner != original_winner

def test_select_weights_should_return_default_weights_when_expense_ratio_is_normal_and_fd_count_above_two():
    weights = select_weights(expense_ratio=0.5, fd_count=3)
    assert weights == {
        "liq": 0.30,
        "spread": 0.20,
        "ladder": 0.20,
        "conc": 0.15,
        "penalty": 0.15,
    }


def test_select_weights_should_return_high_expense_weights_when_expense_ratio_above_point_six_and_fd_count_above_two():
    weights = select_weights(expense_ratio=0.65, fd_count=3)
    assert weights == {
        "liq": 0.40,
        "spread": 0.15,
        "ladder": 0.15,
        "conc": 0.15,
        "penalty": 0.15,
    }


def test_select_weights_should_return_single_fd_weights_when_fd_count_is_one():
    weights = select_weights(expense_ratio=0.3, fd_count=1)
    assert weights == {
        "liq": 0.25,
        "spread": 0.25,
        "ladder": 0.25,
        "conc": 0.15,
        "penalty": 0.10,
    }


def test_select_weights_should_return_single_fd_weights_when_fd_count_is_two():
    weights = select_weights(expense_ratio=0.3, fd_count=2)
    assert weights == {
        "liq": 0.25,
        "spread": 0.25,
        "ladder": 0.25,
        "conc": 0.15,
        "penalty": 0.10,
    }


def test_select_weights_should_sum_to_one_when_any_valid_branch_is_selected():
    cases = [(0.4, 3), (0.7, 3), (0.3, 1), (0.2, 2)]
    for ratio, count in cases:
        weights = select_weights(ratio, count)
        assert sum(weights.values()) == pytest.approx(1.0)


def test_assign_persona_should_return_conservative_saver_when_scores_match_conservative_profile():
    req = make_request(liquidity=85, spread=75, penalty=15, concentration=75, ladder=80)
    assert _assign_persona(req) == "Conservative Saver"


def test_assign_persona_should_return_liquidity_risk_user_when_scores_match_liquidity_risk_profile():
    req = make_request(liquidity=20, spread=30, penalty=75, concentration=40, ladder=25)
    assert _assign_persona(req) == "Liquidity Risk User"


def test_assign_persona_should_return_liquidity_risk_user_when_penalty_override_is_triggered():
    req = make_request(liquidity=80, spread=80, penalty=72, concentration=80, ladder=80)
    assert _assign_persona(req) == "Liquidity Risk User"


def test_assign_persona_should_return_long_term_planner_when_long_term_override_conditions_are_met():
    fds = [
        make_fd(1, 300000, 380000, 36, "2027-01-01", "LONG_TERM"),
        make_fd(2, 300000, 380000, 48, "2028-01-01", "LONG_TERM"),
        make_fd(3, 300000, 380000, 60, "2029-01-01", "LONG_TERM"),
    ]
    req = make_request(liquidity=30, spread=60, penalty=35, concentration=65, ladder=70, fds=fds)
    assert _assign_persona(req) == "Long-Term Planner"


def test_assign_persona_should_return_liquidity_risk_user_when_both_overrides_apply_and_penalty_takes_priority():
    fds = [
        make_fd(1, 300000, 380000, 36, "2027-01-01", "LONG_TERM"),
        make_fd(2, 300000, 380000, 48, "2028-01-01", "LONG_TERM"),
        make_fd(3, 300000, 380000, 60, "2029-01-01", "LONG_TERM"),
    ]
    req = make_request(liquidity=80, spread=80, penalty=72, concentration=80, ladder=70, fds=fds)
    assert _assign_persona(req) == "Liquidity Risk User"


def test_assign_persona_should_return_expected_nearest_persona_when_no_override_applies():
    fds = [
        make_fd(1, 200000, 250000, 36, "2027-01-01", "LONG_TERM"),
        make_fd(2, 300000, 380000, 48, "2028-01-01", "LONG_TERM"),
        make_fd(3, 100000, 120000, 24, "2026-06-01", "LONG_TERM"),
        make_fd(4, 50000, 55000, 6, "2025-12-01", "SHORT_TERM"),
    ]
    req = make_request(liquidity=30, spread=60, penalty=35, concentration=65, ladder=70, fds=fds)

    profile = {
        "liq": 30,
        "spread": 60,
        "penalty": 35,
        "conc": 65,
        "ladder": 70,
    }
    expected = nearest_persona(profile)

    assert _assign_persona(req) == expected


def test_assign_persona_should_return_aggressive_reinvestor_when_scores_match_aggressive_profile():
    fds = [
        make_fd(1, 500000, 600000, 36, "2028-01-01", "LONG_TERM"),
        make_fd(2, 500000, 600000, 36, "2028-06-01", "LONG_TERM"),
    ]
    req = make_request(liquidity=20, spread=40, penalty=50, concentration=30, ladder=30, fds=fds)
    assert _assign_persona(req) == "Aggressive Reinvestor"


def test_assign_persona_should_return_balanced_investor_when_scores_match_balanced_profile():
    req = make_request(liquidity=60, spread=60, penalty=30, concentration=65, ladder=65)
    assert _assign_persona(req) == "Balanced Investor"


@pytest.mark.parametrize(
    "penalty,expected",
    [
        (70, "Balanced Investor"),
        (71, "Liquidity Risk User"),
    ],
)
def test_assign_persona_should_respect_penalty_override_boundary_when_penalty_crosses_seventy(penalty, expected):
    req = make_request(
        liquidity=60,
        spread=60,
        penalty=penalty,
        concentration=65,
        ladder=65,
    )
    assert _assign_persona(req) == expected


@pytest.mark.parametrize(
    "ladder,expected_override",
    [
        (65, False),
        (66, True),
    ],
)
def test_assign_persona_should_respect_long_term_override_boundary_when_ladder_crosses_sixty_five(ladder, expected_override):
    fds = [
        make_fd(1, 300000, 380000, 36, "2027-01-01", "LONG_TERM"),
        make_fd(2, 300000, 380000, 48, "2028-01-01", "LONG_TERM"),
        make_fd(3, 300000, 380000, 60, "2029-01-01", "LONG_TERM"),
        make_fd(4, 100000, 120000, 12, "2026-01-01", "SHORT_TERM"),
        make_fd(5, 100000, 120000, 12, "2026-06-01", "LONG_TERM"),
    ]
    req = make_request(
        liquidity=30,
        spread=60,
        penalty=35,
        concentration=65,
        ladder=ladder,
        fds=fds,
    )

    profile = {
        "liq": req.liquidityScore,
        "spread": req.maturitySpreadScore,
        "penalty": req.penaltyExposure,
        "conc": req.concentrationRisk,
        "ladder": req.ladderScore,
    }

    result = _assign_persona(req)

    if expected_override:
        assert result == "Long-Term Planner"
    else:
        assert result == nearest_persona(profile)


def test_calc_health_score_should_use_single_fd_weights_when_fd_count_is_one():
    req = make_request(liquidity=80, spread=60, penalty=20, concentration=65, ladder=65)
    score = _calc_health_score(req)
    expected = 80 * 0.25 + 60 * 0.25 + 65 * 0.25 + 65 * 0.15 + 80 * 0.10
    assert score == pytest.approx(expected, abs=0.5)


def test_calc_health_score_should_use_high_expense_weights_when_expense_ratio_above_point_six_and_fd_count_above_two():
    fds = [
        make_fd(1, 200000, 230000, 12, "2026-06-01", "SHORT_TERM"),
        make_fd(2, 200000, 260000, 24, "2027-06-01", "LONG_TERM"),
        make_fd(3, 200000, 290000, 36, "2028-06-01", "LONG_TERM"),
    ]
    req = make_request(
        liquidity=80,
        spread=60,
        penalty=20,
        concentration=65,
        ladder=65,
        income=80000,
        expenses=55000,
        fds=fds,
    )
    score = _calc_health_score(req)
    expected = 80 * 0.40 + 60 * 0.15 + 65 * 0.15 + 65 * 0.15 + 80 * 0.15
    assert score == pytest.approx(expected, abs=0.5)


def test_calc_health_score_should_use_default_weights_when_expense_ratio_is_normal_and_fd_count_above_two():
    fds = [
        make_fd(1, 200000, 230000, 12, "2026-06-01", "SHORT_TERM"),
        make_fd(2, 200000, 260000, 24, "2027-06-01", "LONG_TERM"),
        make_fd(3, 200000, 290000, 36, "2028-06-01", "LONG_TERM"),
    ]
    req = make_request(
        liquidity=80,
        spread=60,
        penalty=20,
        concentration=65,
        ladder=65,
        income=80000,
        expenses=40000,
        fds=fds,
    )
    score = _calc_health_score(req)
    expected = 80 * 0.30 + 60 * 0.20 + 65 * 0.20 + 65 * 0.15 + 80 * 0.15
    assert score == pytest.approx(expected, abs=0.5)


def test_calc_health_score_should_not_exceed_hundred_when_all_inputs_are_maximised():
    req = make_request(liquidity=100, spread=100, penalty=0, concentration=100, ladder=100)
    assert _calc_health_score(req) <= 100.0


def test_calc_health_score_should_not_go_below_zero_when_all_inputs_are_minimised():
    req = make_request(liquidity=0, spread=0, penalty=100, concentration=0, ladder=0)
    assert _calc_health_score(req) >= 0.0


def test_calc_health_score_should_return_higher_score_when_portfolio_is_better():
    good = make_request(liquidity=90, spread=90, penalty=10, concentration=90, ladder=90)
    bad = make_request(liquidity=10, spread=10, penalty=90, concentration=10, ladder=10)
    assert _calc_health_score(good) > _calc_health_score(bad)


def test_calc_health_score_should_use_default_branch_when_monthly_income_is_zero():
    fds = [
        make_fd(1, 200000, 230000, 12, "2026-06-01", "SHORT_TERM"),
        make_fd(2, 200000, 260000, 24, "2027-06-01", "LONG_TERM"),
        make_fd(3, 200000, 290000, 36, "2028-06-01", "LONG_TERM"),
    ]
    req = make_request(
        liquidity=80,
        spread=60,
        penalty=20,
        concentration=65,
        ladder=65,
        income=0,
        expenses=40000,
        fds=fds,
    )
    score = _calc_health_score(req)
    expected = 80 * 0.30 + 60 * 0.20 + 65 * 0.20 + 65 * 0.15 + 80 * 0.15
    assert score == pytest.approx(expected, abs=0.5)


@pytest.mark.parametrize(
    "expense,fd_count,expected_liq_weight",
    [
        (48000, 3, 0.30),
        (48001, 3, 0.40),
        (30000, 2, 0.25),
    ],
)
def test_select_weights_should_respect_branch_boundaries_when_expense_ratio_or_fd_count_changes(expense, fd_count, expected_liq_weight):
    fds = [
        make_fd(1, 200000, 230000, 12, "2026-06-01", "SHORT_TERM"),
        make_fd(2, 200000, 260000, 24, "2027-06-01", "LONG_TERM"),
        make_fd(3, 200000, 290000, 36, "2028-06-01", "LONG_TERM"),
    ][:fd_count]

    req = make_request(
        liquidity=80,
        spread=60,
        penalty=20,
        concentration=65,
        ladder=65,
        income=80000,
        expenses=expense,
        fds=fds,
    )
    expense_ratio = req.monthlyExpenses / req.monthlyIncome if req.monthlyIncome > 0 else 0.5
    weights = select_weights(expense_ratio, len(req.fds))

    assert weights["liq"] == expected_liq_weight


def test_generate_recommendation_should_include_emergency_fund_tip_when_liquidity_score_is_below_fifty():
    req = make_request(liquidity=20, spread=70, penalty=20, ladder=70)
    rec = _generate_recommendation(req)
    assert "Your emergency fund is below the 6-month safety benchmark" in rec
    assert "short-term FDs" in rec


def test_generate_recommendation_should_include_ladder_tip_when_ladder_score_is_below_fifty_and_fd_count_is_below_three():
    req = make_request(liquidity=70, spread=70, penalty=20, ladder=20)
    rec = _generate_recommendation(req)
    assert "Industry best practice recommends 3–5 FDs" in rec
    assert "laddering" in rec


def test_generate_recommendation_should_include_spread_tip_when_maturity_spread_score_is_below_forty_and_fd_count_is_at_least_three():
    fds = [
        make_fd(1, 100000, 115000, 12, "2026-01-01", "SHORT_TERM"),
        make_fd(2, 100000, 130000, 24, "2026-03-01", "LONG_TERM"),
        make_fd(3, 100000, 145000, 36, "2026-06-01", "LONG_TERM"),
    ]
    req = make_request(liquidity=70, spread=20, penalty=20, ladder=60, fds=fds)
    rec = _generate_recommendation(req)
    assert "Your FD maturities are clustered" in rec
    assert "reinvestment risk" in rec


def test_generate_recommendation_should_include_penalty_tip_when_penalty_exposure_is_above_sixty():
    req = make_request(liquidity=70, spread=70, penalty=75, ladder=70)
    rec = _generate_recommendation(req)
    assert "expense-to-income ratio" in rec
    assert "high chance of needing to break FDs early" in rec


def test_generate_recommendation_should_include_concentration_tip_when_large_fd_exists_and_concentration_risk_is_below_forty():
    fds = [
        make_fd(1, 600000, 700000, 24, "2027-01-01", "LONG_TERM"),
    ]
    req = make_request(
        liquidity=70,
        spread=70,
        penalty=20,
        concentration=20,
        ladder=70,
        fds=fds,
    )
    rec = _generate_recommendation(req)
    assert "DICGC insures only ₹5 lakh per depositor per bank" in rec
    assert "splitting large FDs across different banks" in rec


def test_generate_recommendation_should_include_very_long_fd_tip_when_duration_exceeds_forty_eight_months():
    fds = [
        make_fd(1, 300000, 400000, 60, "2030-01-01", "LONG_TERM"),
    ]
    req = make_request(
        liquidity=80,
        spread=80,
        penalty=20,
        concentration=80,
        ladder=80,
        fds=fds,
    )
    rec = _generate_recommendation(req)
    assert "tenure > 4 years" in rec
    assert "premature withdrawal incurs a 1% penalty" in rec


def test_generate_recommendation_should_include_healthy_tip_when_no_other_tip_applies():
    req = make_request(liquidity=85, spread=85, penalty=10, concentration=85, ladder=85)
    rec = _generate_recommendation(req)
    assert "looking strong" in rec
    assert "Maintain your current laddering strategy" in rec


@pytest.mark.parametrize(
    "liquidity,should_contain_tip",
    [
        (49, True),
        (50, False),
    ],
)
def test_generate_recommendation_should_respect_liquidity_boundary_when_score_crosses_fifty(liquidity, should_contain_tip):
    req = make_request(liquidity=liquidity, spread=70, penalty=20, ladder=70)
    rec = _generate_recommendation(req)
    phrase = "Your emergency fund is below the 6-month safety benchmark"
    assert (phrase in rec) is should_contain_tip


@pytest.mark.parametrize(
    "ladder,should_contain_tip",
    [
        (49, True),
        (50, False),
    ],
)
def test_generate_recommendation_should_respect_ladder_boundary_when_score_crosses_fifty(ladder, should_contain_tip):
    req = make_request(liquidity=70, spread=70, penalty=20, ladder=ladder)
    rec = _generate_recommendation(req)
    phrase = "Industry best practice recommends 3–5 FDs"
    assert (phrase in rec) is should_contain_tip


@pytest.mark.parametrize(
    "spread,should_contain_tip",
    [
        (39, True),
        (40, False),
    ],
)
def test_generate_recommendation_should_respect_spread_boundary_when_score_crosses_forty(spread, should_contain_tip):
    fds = [
        make_fd(1, 100000, 115000, 12, "2026-01-01", "SHORT_TERM"),
        make_fd(2, 100000, 130000, 24, "2026-03-01", "LONG_TERM"),
        make_fd(3, 100000, 145000, 36, "2026-06-01", "LONG_TERM"),
    ]
    req = make_request(liquidity=70, spread=spread, penalty=20, ladder=60, fds=fds)
    rec = _generate_recommendation(req)
    phrase = "Your FD maturities are clustered"
    assert (phrase in rec) is should_contain_tip


@pytest.mark.parametrize(
    "penalty,should_contain_tip",
    [
        (60, False),
        (61, True),
    ],
)
def test_generate_recommendation_should_respect_penalty_boundary_when_exposure_crosses_sixty(penalty, should_contain_tip):
    req = make_request(liquidity=70, spread=70, penalty=penalty, ladder=70)
    rec = _generate_recommendation(req)
    phrase = "high chance of needing to break FDs early"
    assert (phrase in rec) is should_contain_tip


@pytest.mark.parametrize(
    "duration_months,should_contain_tip",
    [
        (48, False),
        (49, True),
    ],
)
def test_generate_recommendation_should_respect_very_long_fd_boundary_when_duration_crosses_forty_eight_months(duration_months, should_contain_tip):
    fds = [
        make_fd(1, 300000, 400000, duration_months, "2030-01-01", "LONG_TERM"),
    ]
    req = make_request(
        liquidity=80,
        spread=80,
        penalty=20,
        concentration=80,
        ladder=80,
        fds=fds,
    )
    rec = _generate_recommendation(req)
    phrase = "tenure > 4 years"
    assert (phrase in rec) is should_contain_tip


def test_generate_analytics_should_return_all_fields_when_request_is_valid():
    req = make_request(liquidity=70, spread=60, penalty=25, concentration=65, ladder=65)
    result = generate_analytics(req)
    assert result.persona is not None and len(result.persona) > 0
    assert 0 <= result.portfolioHealthScore <= 100
    assert result.recommendation is not None and len(result.recommendation) > 0


def test_generate_analytics_should_return_higher_health_score_when_good_portfolio_is_compared_with_bad():
    good = make_request(liquidity=90, spread=90, penalty=10, concentration=90, ladder=90)
    bad = make_request(liquidity=10, spread=10, penalty=90, concentration=10, ladder=10)
    assert generate_analytics(good).portfolioHealthScore > generate_analytics(bad).portfolioHealthScore


def test_generate_analytics_should_return_valid_persona_when_request_is_processed():
    req = make_request()
    result = generate_analytics(req)
    valid_personas = {
        "Conservative Saver",
        "Liquidity Risk User",
        "Long-Term Planner",
        "Aggressive Reinvestor",
        "Balanced Investor",
    }
    assert result.persona in valid_personas