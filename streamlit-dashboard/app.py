import streamlit as st
import requests
import pandas as pd
import plotly.graph_objects as go
import plotly.express as px
from datetime import date, datetime
import json
API = "http://spring-service:8080"
GO_URL      = "http://go-risk-engine:8081"
FASTAPI_URL = "http://fastapi-analytics:8082"
st.set_page_config(
    page_title="FD Management",
    page_icon="🛡️",
    layout="wide",
    initial_sidebar_state="expanded"
)
st.markdown("""
<style>
@import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@300;400;500;700&family=Sora:wght@300;400;600;700&display=swap');
html, body, [class*="css"] {
    font-family: 'Sora', sans-serif;
}

/* Header */
.fd-header {
    background: linear-gradient(135deg, #0A0F1E 0%, #0D1B2A 50%, #0A1628 100%);
    border-bottom: 1px solid #00D4AA22;
    padding: 1.5rem 2rem;
    margin: -1rem -1rem 2rem -1rem;
}
.fd-title {
    font-family: 'JetBrains Mono', monospace;
    font-size: 2rem;
    font-weight: 700;
    color: #00D4AA;
    letter-spacing: -0.02em;
    margin: 0;
}
.fd-subtitle {
    color: #64748B;
    font-size: 0.85rem;
    margin: 0.25rem 0 0 0;
    font-family: 'JetBrains Mono', monospace;
    letter-spacing: 0.05em;
}

/* Metric cards */
.metric-card {
    background: #111827;
    border: 1px solid #1E293B;
    border-radius: 12px;
    padding: 1.25rem 1.5rem;
    position: relative;
    overflow: hidden;
}
.metric-card::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0;
    height: 3px;
    background: linear-gradient(90deg, #00D4AA, #0EA5E9);
}
.metric-label {
    color: #64748B;
    font-size: 0.75rem;
    font-family: 'JetBrains Mono', monospace;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    margin-bottom: 0.5rem;
}
.metric-value {
    color: #E2E8F0;
    font-size: 1.75rem;
    font-weight: 700;
    font-family: 'JetBrains Mono', monospace;
    line-height: 1;
}
.metric-sub {
    color: #475569;
    font-size: 0.75rem;
    margin-top: 0.35rem;
}

/* Persona badge */
.persona-badge {
    display: inline-block;
    padding: 0.4rem 1rem;
    border-radius: 999px;
    font-size: 0.8rem;
    font-weight: 600;
    font-family: 'JetBrains Mono', monospace;
    letter-spacing: 0.05em;
}

/* Score ring labels */
.score-label {
    font-family: 'JetBrains Mono', monospace;
    font-size: 0.7rem;
    color: #64748B;
    text-transform: uppercase;
    letter-spacing: 0.1em;
}

/* Section headers */
.section-header {
    font-family: 'JetBrains Mono', monospace;
    font-size: 0.75rem;
    color: #00D4AA;
    text-transform: uppercase;
    letter-spacing: 0.15em;
    border-bottom: 1px solid #1E293B;
    padding-bottom: 0.5rem;
    margin-bottom: 1rem;
}

/* FD row */
.fd-row {
    background: #111827;
    border: 1px solid #1E293B;
    border-radius: 8px;
    padding: 1rem 1.25rem;
    margin-bottom: 0.75rem;
    display: flex;
    align-items: center;
    gap: 1rem;
}
.fd-type-pill {
    font-size: 0.65rem;
    padding: 0.2rem 0.6rem;
    border-radius: 4px;
    font-family: 'JetBrains Mono', monospace;
    font-weight: 600;
    letter-spacing: 0.05em;
}
.short-term { background: #064E3B; color: #34D399; }
.long-term  { background: #1E3A5F; color: #60A5FA; }

/* Recommendation box */
.rec-box {
    background: #0D1B2A;
    border: 1px solid #00D4AA33;
    border-left: 3px solid #00D4AA;
    border-radius: 8px;
    padding: 1rem 1.25rem;
    margin-bottom: 0.75rem;
    font-size: 0.875rem;
    color: #CBD5E1;
    line-height: 1.6;
}

/* Success / error toast style */
.toast-success {
    background: #064E3B;
    border: 1px solid #34D399;
    border-radius: 8px;
    padding: 0.75rem 1rem;
    color: #34D399;
    font-family: 'JetBrains Mono', monospace;
    font-size: 0.8rem;
}
.toast-error {
    background: #450A0A;
    border: 1px solid #F87171;
    border-radius: 8px;
    padding: 0.75rem 1rem;
    color: #F87171;
    font-family: 'JetBrains Mono', monospace;
    font-size: 0.8rem;
}
</style>
""", unsafe_allow_html=True)

def api_post(endpoint, payload):
    try:
        r = requests.post(f"{API}{endpoint}", json=payload, timeout=10)
        return r.json(), r.status_code
    except Exception as e:
        return {"error": str(e)}, 500

def api_get(endpoint):
    try:
        r = requests.get(f"{API}{endpoint}", timeout=10)
        return r.json(), r.status_code
    except Exception as e:
        return {"error": str(e)}, 500

def fmt_inr(val):
    if val is None: return "—"
    return f"₹{float(val):,.0f}"

def score_color(score):
    if score is None: return "#475569"
    s = float(score)
    if s >= 70: return "#34D399"
    if s >= 40: return "#FBBF24"
    return "#F87171"

def persona_color(persona):
    colors = {
        "Conservative Saver":   ("#064E3B", "#34D399"),
        "Liquidity Risk User":  ("#450A0A", "#F87171"),
        "Long-Term Planner":    ("#1E3A5F", "#60A5FA"),
        "Aggressive Reinvestor":("#451A03", "#FB923C"),
        "Balanced Investor":    ("#1E1B4B", "#A78BFA"),
    }
    return colors.get(persona, ("#1E293B", "#94A3B8"))

def gauge_chart(value, title, color):
    fig = go.Figure(go.Indicator(
        mode="gauge+number",
        value=value if value else 0,
        number={"font": {"size": 28, "color": color, "family": "JetBrains Mono"},
                "suffix": ""},
        gauge={
            "axis": {"range": [0, 100], "tickfont": {"size": 9, "color": "#475569"},
                     "tickcolor": "#1E293B"},
            "bar": {"color": color, "thickness": 0.25},
            "bgcolor": "#111827",
            "bordercolor": "#1E293B",
            "steps": [
                {"range": [0, 40],  "color": "#1A0A0A"},
                {"range": [40, 70], "color": "#1A1500"},
                {"range": [70, 100],"color": "#0A1A12"},
            ],
            "threshold": {
                "line": {"color": color, "width": 2},
                "thickness": 0.75,
                "value": value if value else 0
            }
        },
        title={"text": title,
               "font": {"size": 11, "color": "#64748B", "family": "JetBrains Mono"}}
    ))
    fig.update_layout(
        height=200,
        margin=dict(t=40, b=10, l=20, r=20),
        paper_bgcolor="rgba(0,0,0,0)",
        plot_bgcolor="rgba(0,0,0,0)",
        font={"family": "JetBrains Mono"}
    )
    return fig

st.markdown("""
<div class="fd-header">
  <p class="fd-title">🛡️ FD Management</p>
  <p class="fd-subtitle">// INTELLIGENT FIXED DEPOSIT PORTFOLIO & LIQUIDITY MANAGEMENT</p>
</div>
""", unsafe_allow_html=True)
with st.sidebar:
    st.markdown('<p class="section-header">Navigation</p>', unsafe_allow_html=True)
    page = st.radio("", ["📊 Portfolio Dashboard", "➕ Create User", "💰 Create FD", "🏧 Withdraw FD"],
                    label_visibility="collapsed")

    st.markdown("---")
    st.markdown('<p class="section-header">Service Status</p>', unsafe_allow_html=True)

    for name, url in [("Spring Boot", f"{API}/health"),
                      ("Go Risk Engine", f"{GO_URL}/health"),
                      ("FastAPI Analytics", f"{FASTAPI_URL}/health")]:
        try:
            r = requests.get(url, timeout=3)
            status = "🟢 Online" if r.status_code == 200 else "🔴 Error"
        except:
            status = "🔴 Offline"
        st.markdown(f"""
        <div style="display:flex;justify-content:space-between;align-items:center;
                    padding:0.4rem 0;border-bottom:1px solid #1E293B;">
            <span style="font-family:'JetBrains Mono';font-size:0.75rem;color:#94A3B8">{name}</span>
            <span style="font-size:0.7rem;color:#64748B">{status}</span>
        </div>
        """, unsafe_allow_html=True)

if page == "📊 Portfolio Dashboard":
    st.markdown('<p class="section-header">Portfolio Dashboard</p>', unsafe_allow_html=True)

    user_id = st.number_input(
    "User ID",
    min_value=1,
    value=st.session_state.get("last_user_id", 1),
    step=1,
    key="port_uid"
)

    if st.button("Load Portfolio", type="primary", use_container_width=True):
        data, status = api_get(f"/users/{user_id}/portfolio")

        if status != 200 or "error" in data:
            st.markdown(f'<div class="toast-error">❌ {data.get("error", "Failed to load portfolio")}</div>',
                        unsafe_allow_html=True)
        else:
            user = data.get("user", {})
            fds  = data.get("activeFds", [])
            prof = data.get("financialProfile") or {}
            st.markdown(f"""
            <div style="background:#111827;border:1px solid #1E293B;border-radius:12px;
                        padding:1.25rem 1.5rem;margin-bottom:1.5rem;">
                <div style="display:flex;align-items:center;gap:1rem;margin-bottom:0.75rem;">
                    <div style="width:44px;height:44px;background:linear-gradient(135deg,#00D4AA,#0EA5E9);
                                border-radius:50%;display:flex;align-items:center;justify-content:center;
                                font-size:1.1rem;font-weight:700;color:#0A0F1E;">
                        {user.get('name','?')[0].upper()}
                    </div>
                    <div>
                        <div style="font-size:1.1rem;font-weight:600;color:#E2E8F0">{user.get('name','—')}</div>
                        <div style="font-size:0.75rem;color:#64748B;font-family:'JetBrains Mono'">{user.get('email','—')}</div>
                    </div>
                </div>
                <div style="display:grid;grid-template-columns:1fr 1fr 1fr 1fr;gap:1rem;">
                    <div><div class="metric-label">Monthly Income</div>
                         <div style="color:#34D399;font-family:'JetBrains Mono';font-weight:600">{fmt_inr(user.get('monthlyIncome'))}</div></div>
                    <div><div class="metric-label">Monthly Expenses</div>
                         <div style="color:#F87171;font-family:'JetBrains Mono';font-weight:600">{fmt_inr(user.get('monthlyExpenses'))}</div></div>
                    <div><div class="metric-label">Total Principal</div>
                         <div style="color:#60A5FA;font-family:'JetBrains Mono';font-weight:600">{fmt_inr(data.get('totalPrincipal'))}</div></div>
                    <div><div class="metric-label">Total Maturity Value</div>
                         <div style="color:#A78BFA;font-family:'JetBrains Mono';font-weight:600">{fmt_inr(data.get('totalMaturityValue'))}</div></div>
                </div>
            </div>
            """, unsafe_allow_html=True)

            if prof:
                persona = prof.get("persona", "Unknown")
                bg, fg = persona_color(persona)

                st.markdown(f"""
                <div style="display:flex;align-items:center;gap:1rem;margin-bottom:1.5rem;">
                    <span style="color:#64748B;font-family:'JetBrains Mono';font-size:0.75rem;">PERSONA</span>
                    <span style="background:{bg};color:{fg};padding:0.35rem 1rem;border-radius:999px;
                                 font-family:'JetBrains Mono';font-size:0.8rem;font-weight:600;
                                 border:1px solid {fg}44;">{persona}</span>
                </div>
                """, unsafe_allow_html=True)

                c1, c2, c3 = st.columns(3)
                scores_row1 = [
                    (c1, prof.get("portfolioHealthScore"), "HEALTH SCORE"),
                    (c2, prof.get("liquidityScore"),       "LIQUIDITY"),
                    (c3, prof.get("ladderScore"),          "LADDER QUALITY"),
                ]
                for col, val, title in scores_row1:
                    with col:
                        v = float(val) if val else 0
                        color = score_color(v)
                        st.plotly_chart(gauge_chart(v, title, color),
                                        use_container_width=True, config={"displayModeBar": False})

                c4, c5 = st.columns(2)
                scores_row2 = [
                    (c4, prof.get("maturitySpreadScore"), "MATURITY SPREAD"),
                    (c5, prof.get("penaltyRisk"),         "PENALTY RISK"),
                ]
                for col, val, title in scores_row2:
                    with col:
                        v = float(val) if val else 0
                        color = score_color(v) if "RISK" not in title else (
                            "#34D399" if v < 30 else "#FBBF24" if v < 60 else "#F87171")
                        st.plotly_chart(gauge_chart(v, title, color),
                                        use_container_width=True, config={"displayModeBar": False})

                conc = prof.get("concentrationRisk")
                if conc is not None:
                    cv = float(conc)
                    conc_color = "#34D399" if cv > 65 else "#FBBF24" if cv > 35 else "#F87171"
                    st.markdown(f"""
                    <div style="background:#111827;border:1px solid #1E293B;border-radius:8px;
                                padding:0.75rem 1.25rem;margin-bottom:1rem;">
                        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:0.5rem;">
                            <span style="font-family:'JetBrains Mono';font-size:0.7rem;color:#64748B;
                                         letter-spacing:0.1em;">CONCENTRATION RISK (HHI-based diversification)</span>
                            <span style="font-family:'JetBrains Mono';font-size:0.9rem;font-weight:700;color:{conc_color}">{cv:.1f}/100</span>
                        </div>
                        <div style="background:#1E293B;border-radius:4px;height:6px;">
                            <div style="background:{conc_color};border-radius:4px;height:6px;width:{cv}%"></div>
                        </div>
                        <div style="font-size:0.7rem;color:#475569;margin-top:0.35rem;font-family:'JetBrains Mono'">
                            Higher = better diversification · DICGC insures ₹5L per bank
                        </div>
                    </div>
                    """, unsafe_allow_html=True)
                rec = prof.get("recommendation", "")
                if rec:
                    st.markdown('<p class="section-header" style="margin-top:1rem">Recommendations</p>',
                                unsafe_allow_html=True)
                    for tip in rec.split(" | "):
                        if tip.strip():
                            st.markdown(f'<div class="rec-box">💡 {tip.strip()}</div>',
                                        unsafe_allow_html=True)
            if fds:
                st.markdown('<p class="section-header" style="margin-top:1.5rem">Active Fixed Deposits</p>',
                            unsafe_allow_html=True)
                df = pd.DataFrame(fds)
                fig = go.Figure()
                fig.add_trace(go.Bar(
                    name="Principal",
                    x=[f"FD #{fd['id']}" for fd in fds],
                    y=[float(fd['principal']) for fd in fds],
                    marker_color="#0EA5E9",
                    marker_line_width=0,
                ))
                fig.add_trace(go.Bar(
                    name="Maturity Value",
                    x=[f"FD #{fd['id']}" for fd in fds],
                    y=[float(fd['maturityAmount']) for fd in fds],
                    marker_color="#00D4AA",
                    marker_line_width=0,
                ))
                fig.update_layout(
                    barmode="group",
                    paper_bgcolor="rgba(0,0,0,0)",
                    plot_bgcolor="rgba(0,0,0,0)",
                    font={"family": "JetBrains Mono", "color": "#94A3B8", "size": 11},
                    legend={"bgcolor": "rgba(0,0,0,0)", "font": {"size": 11}},
                    xaxis={"gridcolor": "#1E293B", "linecolor": "#1E293B"},
                    yaxis={"gridcolor": "#1E293B", "linecolor": "#1E293B",
                           "tickprefix": "₹", "tickformat": ",.0f"},
                    margin=dict(t=20, b=10, l=10, r=10),
                    height=260,
                )
                st.plotly_chart(fig, use_container_width=True, config={"displayModeBar": False})
                for fd in fds:
                    fd_type = fd.get("fdType", "")
                    pill_class = "short-term" if fd_type == "SHORT_TERM" else "long-term"
                    pill_label = "SHORT TERM" if fd_type == "SHORT_TERM" else "LONG TERM"
                    interest = (float(fd['maturityAmount']) - float(fd['principal']))
                    st.markdown(f"""
                    <div class="fd-row">
                        <div style="min-width:60px">
                            <div style="font-family:'JetBrains Mono';color:#00D4AA;font-weight:700">#{fd['id']}</div>
                            <span class="fd-type-pill {pill_class}">{pill_label}</span>
                        </div>
                        <div style="flex:1;display:grid;grid-template-columns:repeat(5,1fr);gap:0.5rem">
                            <div><div class="metric-label">Principal</div>
                                 <div style="color:#E2E8F0;font-family:'JetBrains Mono';font-size:0.9rem;font-weight:600">{fmt_inr(fd['principal'])}</div></div>
                            <div><div class="metric-label">Maturity</div>
                                 <div style="color:#34D399;font-family:'JetBrains Mono';font-size:0.9rem;font-weight:600">{fmt_inr(fd['maturityAmount'])}</div></div>
                            <div><div class="metric-label">Rate</div>
                                 <div style="color:#FBBF24;font-family:'JetBrains Mono';font-size:0.9rem;font-weight:600">{fd['interestRate']}%</div></div>
                            <div><div class="metric-label">Duration</div>
                                 <div style="color:#A78BFA;font-family:'JetBrains Mono';font-size:0.9rem;font-weight:600">{fd['durationMonths']} mo</div></div>
                            <div><div class="metric-label">Matures On</div>
                                 <div style="color:#94A3B8;font-family:'JetBrains Mono';font-size:0.85rem">{fd['maturityDate']}</div></div>
                        </div>
                        <div style="text-align:right;min-width:100px">
                            <div class="metric-label">Interest</div>
                            <div style="color:#00D4AA;font-family:'JetBrains Mono';font-size:0.9rem;font-weight:600">+{fmt_inr(interest)}</div>
                        </div>
                    </div>
                    """, unsafe_allow_html=True)
                st.markdown('<p class="section-header" style="margin-top:1rem">Maturity Timeline</p>',
                            unsafe_allow_html=True)
                timeline_fig = go.Figure()
                for fd in fds:
                    timeline_fig.add_trace(go.Scatter(
                        x=[fd['maturityDate']],
                        y=[float(fd['maturityAmount'])],
                        mode="markers+text",
                        marker=dict(size=16, color="#00D4AA" if fd['fdType']=="SHORT_TERM" else "#60A5FA",
                                    line=dict(width=2, color="#0A0F1E")),
                        text=[f"FD #{fd['id']}"],
                        textposition="top center",
                        textfont=dict(size=10, family="JetBrains Mono", color="#94A3B8"),
                        name=f"FD #{fd['id']}",
                        showlegend=False,
                    ))
                timeline_fig.update_layout(
                    paper_bgcolor="rgba(0,0,0,0)",
                    plot_bgcolor="rgba(0,0,0,0)",
                    font={"family": "JetBrains Mono", "color": "#94A3B8", "size": 10},
                    xaxis={"gridcolor": "#1E293B", "linecolor": "#1E293B"},
                    yaxis={"gridcolor": "#1E293B", "linecolor": "#1E293B",
                           "tickprefix": "₹", "tickformat": ",.0f", "title": "Maturity Value"},
                    margin=dict(t=20, b=10, l=10, r=10),
                    height=220,
                )
                st.plotly_chart(timeline_fig, use_container_width=True,
                                config={"displayModeBar": False})
            else:
                st.markdown("""
                <div style="text-align:center;padding:3rem;color:#475569;
                            font-family:'JetBrains Mono';font-size:0.85rem;">
                    No active FDs found. Create one to see your portfolio analysis.
                </div>
                """, unsafe_allow_html=True)

elif page == "➕ Create User":
    st.markdown('<p class="section-header">Create New User</p>', unsafe_allow_html=True)

    with st.form("create_user_form"):
        c1, c2 = st.columns(2)
        with c1:
            name  = st.text_input("Full Name", placeholder="Amaresh")
            email = st.text_input("Email", placeholder="lakshman@gmail.com")
        with c2:
            income   = st.number_input("Monthly Income (₹)", min_value=1000, value=80000, step=1000)
            expenses = st.number_input("Monthly Expenses (₹)", min_value=500, value=40000, step=500)

        submitted = st.form_submit_button("Create User", type="primary", use_container_width=True)

    if submitted:
        if not name or not email:
            st.error("Name and email are required.")
        else:
            data, status = api_post("/users", {
                "name": name, "email": email,
                "monthlyIncome": income, "monthlyExpenses": expenses
            })
            if status == 200 and "id" in data:
                st.markdown(f"""
                <div class="toast-success">
                    ✅ User created — ID: <strong>{data['id']}</strong> · {data['name']} · {data['email']}
                </div>
                """, unsafe_allow_html=True)
                st.session_state["last_user_id"] = data["id"]
            else:
                st.markdown(f'<div class="toast-error">❌ {data.get("error", "Failed to create user")}</div>',
                            unsafe_allow_html=True)
elif page == "💰 Create FD":
    st.markdown('<p class="section-header">Create Fixed Deposit</p>', unsafe_allow_html=True)

    c1, c2 = st.columns(2)
    with c1:
        uid       = st.number_input("User ID", min_value=1,
                                     value=st.session_state.get("last_user_id", 1), step=1)
        principal = st.number_input("Principal Amount (₹)", min_value=1000, value=200000, step=1000)
        rate      = st.number_input("Interest Rate (%)", min_value=0.1, max_value=20.0,
                                     value=7.5, step=0.1)
    with c2:
        duration   = st.number_input("Duration (months)", min_value=1, max_value=120, value=24, step=1)
        start_date = st.date_input("Start Date", value=date.today())
    import math
    r = rate / 100
    t = duration / 12
    maturity_preview = principal * math.pow(1 + r, t)
    interest_preview = maturity_preview - principal
    fd_type_preview  = "SHORT TERM (≤ 12 months)" if duration <= 12 else "LONG TERM (> 12 months)"
    returns_pct      = (interest_preview / principal) * 100

    st.markdown(f"""
    <div style="background:#0D1B2A;border:1px solid #00D4AA33;border-radius:8px;
                padding:1rem 1.25rem;margin:0.75rem 0;">
        <div style="font-family:'JetBrains Mono';font-size:0.7rem;color:#00D4AA;
                    letter-spacing:0.1em;margin-bottom:0.75rem;">⚡ LIVE PREVIEW</div>
        <div style="display:grid;grid-template-columns:1fr 1fr 1fr 1fr;gap:1rem;">
            <div><div class="metric-label">Maturity Amount</div>
                 <div style="color:#34D399;font-family:'JetBrains Mono';font-size:1.1rem;font-weight:700">
                     ₹{maturity_preview:,.0f}</div></div>
            <div><div class="metric-label">Interest Earned</div>
                 <div style="color:#00D4AA;font-family:'JetBrains Mono';font-size:1.1rem;font-weight:700">
                     +₹{interest_preview:,.0f}</div></div>
            <div><div class="metric-label">Total Returns</div>
                 <div style="color:#FBBF24;font-family:'JetBrains Mono';font-size:1.1rem;font-weight:700">
                     {returns_pct:.2f}%</div></div>
            <div><div class="metric-label">FD Type</div>
                 <div style="color:#A78BFA;font-family:'JetBrains Mono';font-size:0.85rem;font-weight:600">
                     {fd_type_preview}</div></div>
        </div>
    </div>
    """, unsafe_allow_html=True)

    if st.button("Create FD", type="primary", use_container_width=True, key="create_fd_btn"):
        data, status = api_post("/fds", {
            "userId": uid,
            "principal": principal,
            "interestRate": rate,
            "durationMonths": duration,
            "startDate": str(start_date)
        })
        if status == 200 and "id" in data:
            st.markdown(f"""
            <div class="toast-success">
                ✅ FD #{data['id']} created · Principal: ₹{float(data['principal']):,.0f}
                · Matures: {data['maturityDate']} · Maturity Value: ₹{float(data['maturityAmount']):,.0f}
            </div>
            """, unsafe_allow_html=True)
            st.session_state["last_fd_id"] = data["id"]
        else:
            st.markdown(f'<div class="toast-error">❌ {data.get("error", "Failed to create FD")}</div>',
                        unsafe_allow_html=True)
elif page == "🏧 Withdraw FD":
    st.markdown('<p class="section-header">Simulate Premature Withdrawal</p>', unsafe_allow_html=True)

    st.markdown("""
    <div style="background:#1A0A0A;border:1px solid #F8717133;border-radius:8px;
                padding:0.75rem 1rem;margin-bottom:1.5rem;
                font-family:'JetBrains Mono';font-size:0.8rem;color:#FCA5A5;">
        ⚠️  Premature withdrawal incurs a 1% penalty on principal and reduces interest earned.
    </div>
    """, unsafe_allow_html=True)

    with st.form("withdraw_form"):
        fd_id = st.number_input("FD ID to Withdraw",
                                 min_value=1,
                                 value=st.session_state.get("last_fd_id", 1),
                                 step=1)
        submitted = st.form_submit_button("⚠️ Confirm Withdrawal", type="primary", use_container_width=True)

    if submitted:
        data, status = api_post("/withdraw", {"fdId": fd_id})
        if status == 200 and "fdId" in data:
            penalty   = float(data.get("penaltyAmount", 0))
            principal = float(data.get("principal", 0))
            interest  = float(data.get("interestEarned", 0))
            net       = float(data.get("netPayout", 0))

            st.markdown(f"""
            <div style="background:#111827;border:1px solid #1E293B;border-radius:12px;
                        padding:1.5rem;margin-top:1rem;">
                <div style="font-family:'JetBrains Mono';font-size:0.7rem;color:#64748B;
                            letter-spacing:0.1em;margin-bottom:1rem;">WITHDRAWAL SUMMARY — FD #{fd_id}</div>
                <div style="display:grid;grid-template-columns:1fr 1fr 1fr 1fr;gap:1rem;">
                    <div><div class="metric-label">Principal</div>
                         <div style="color:#E2E8F0;font-family:'JetBrains Mono';font-size:1.1rem;font-weight:700">
                             ₹{principal:,.0f}</div></div>
                    <div><div class="metric-label">Interest Earned</div>
                         <div style="color:#34D399;font-family:'JetBrains Mono';font-size:1.1rem;font-weight:700">
                             +₹{interest:,.2f}</div></div>
                    <div><div class="metric-label">Penalty (1%)</div>
                         <div style="color:#F87171;font-family:'JetBrains Mono';font-size:1.1rem;font-weight:700">
                             -₹{penalty:,.2f}</div></div>
                    <div><div class="metric-label">Net Payout</div>
                         <div style="color:#00D4AA;font-family:'JetBrains Mono';font-size:1.25rem;font-weight:700">
                             ₹{net:,.2f}</div></div>
                </div>
                <div style="margin-top:1rem;padding-top:1rem;border-top:1px solid #1E293B;
                            color:#64748B;font-family:'JetBrains Mono';font-size:0.75rem;">
                    {data.get('message', '')}
                </div>
            </div>
            """, unsafe_allow_html=True)
        else:
            st.markdown(f'<div class="toast-error">❌ {data.get("error", "Withdrawal failed")}</div>',
                        unsafe_allow_html=True)