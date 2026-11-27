# NotaBigDeal — Enterprise B2B Pricing Engine, Deal Desk & Negotiation Workstation

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35%2C%20compileSdk%2036)-green.svg)](https://developer.android.com)
[![Release](https://img.shields.io/badge/Release-Signed%20APK%20v1.4.2-10B981.svg)](https://github.com/smokingjohnny13-rgb/b2b-financial-calculator/releases/tag/v1.4.2)
[![AlternativeTo](https://img.shields.io/badge/AlternativeTo-NotaBigDeal-00AEEF.svg)](https://alternativeto.net/software/notabigdeal/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/Tests-86%20Unit%20%2B%206%20Firestore%20Rules%20(100%25)-brightgreen.svg)]()
[![Architecture](https://img.shields.io/badge/Architecture-Modular%20MVVM%20%2B%20AppContainer%20DI-orange.svg)]()
[![AI Engine](https://img.shields.io/badge/AI-Gemini%203.8%20Flash%20%2B%200ms%20Offline-blueviolet.svg)]()
[![Reports](https://img.shields.io/badge/Reports-2--Page%20Vector%20PDF%20%2B%20Excel%2FCSV%20(BOM)-red.svg)]()
[![License: AGPL v3](https://img.shields.io/badge/License-AGPLv3%20%2F%20Commercial-blue.svg)](LICENSE)

[🇬🇧 English](README.md) | [🇷🇺 Читать на русском](README_RU.md)

> **Eliminate margin leakage at the negotiation table.** NotaBigDeal is an offline-first, executive-grade financial calculation engine, pricing governance desk, and deal-structuring workstation designed for Key Account Managers (KAMs), Commercial Directors, and CFOs. Structure Net-Net retail price waterfalls, balance buyer concessions with compensatory "Give-Get" trades, model working capital (NWC) and invoice factoring arbitrage, model continuous and floating-rate financing schedules, stress-test EBITDA with McKinsey Tornado charts, and export boardroom-ready 2-page PDF proposals and Excel/CSV financial models directly from your Android smartphone — without laptops, spreadsheets, or broken formulas.

---

## Visual Overview

<p align="center">
  <img src="app/src/main/assets/screenshots/deal_approval.jpg" width="24%" alt="Deal Approval & Traffic Light Scoring Desk" />
  <img src="app/src/main/assets/screenshots/price_waterfall.jpg" width="24%" alt="Retail Price Waterfall & Net-Net Pocket Margin" />
  <img src="app/src/main/assets/screenshots/analytics_cashflow.jpg" width="24%" alt="12-Month ROI & P&L Cashflow Trajectory" />
  <img src="app/src/main/assets/screenshots/executive_one_pager.jpg" width="24%" alt="Executive One-Pager KAM Negotiation Limits & BATNA" />
</p>

---

## Why NotaBigDeal?

During high-stakes commercial negotiations with retail supermarket chains and enterprise procurement committees, million-dollar margin commitments are made in minutes. When a buyer demands:
* an extra **+45 days payment deferral**;
* an additional **5% on-invoice front discount**; and
* an **8.5% off-invoice retro-rebate** with listing fees;

a commercial leader needs answers within seconds:
1. **True Pocket Net-Net Margin**: What remains after all layered discounts, rebates, logistics charges, and SLA fines?
2. **Working Capital & Carrying Cost**: How much cash is frozen in accounts receivable ($AR$), and does invoice factoring beat bank overdraft?
3. **Required Counter-Trades ("Give-Get")**: Exactly how much volume lift or price adjustment must be demanded in return so corporate EBITDA is not destroyed?
4. **Profit Sensitivity Leverage**: Is it safer to concede on price or volume? (The McKinsey model proves price defense is typically $2\text{x}$–$4\text{x}$ more powerful than chasing volume).
5. **Funding Architecture**: How do floating benchmark rates (SOFR/EURIBOR/Central Bank Key Rate + spread) or multi-currency tranches affect long-term liquidity and cash flow?

**NotaBigDeal** packages Wall Street financial modeling into an ergonomic, tactile, and responsive mobile app built with Jetpack Compose.

---

## Core Feature Modules & Financial Engines

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   NotaBigDeal Core Architecture                                  │
├───────────────────────────────┬──────────────────────────────────┬───────────────────────────────┤
│    COMMERCIAL PRICING DESK    │    FINANCING & WORKING CAPITAL   │    GOVERNANCE & ANALYTICS     │
│  • Retail Price Waterfall     │  • Net Working Capital (NWC)     │  • Boardroom 2-Page Vector PDF│
│  • Give-Get Concession Engine │  • Cash Conversion Cycle (CCC)   │  • Excel / CSV UTF-8 BOM Export│
│  • Tornado Profit Sensitivity │  • Invoice Factoring Arbitrage   │  • Deal Scoring Traffic Light │
│  • Promo Break-Even Solver    │  • Floating Rate & Grace Periods │  • Plan-Fact Variance Bridge  │
│  • Landed Cost (FOB ➔ Whs)    │  • Early Repayment Calculator    │  • Gemini 3.8 Flash AI Audit  │
│  • Commercial Document OCR    │  • Multi-Currency Debt Basket    │  • Cloud Vault (Firestore)    │
└───────────────────────────────┴──────────────────────────────────┴───────────────────────────────┘
```

### 1. Retail Price Waterfall & Net-Net Pocket Margin Engine
Deconstructs transactions from consumer shelf RSP down to supplier pocket cash:
- **Shelf Price (RSP)** with VAT and Ex-VAT baseline;
- **Retailer Margin Deduction**: Gross margin taken by the supermarket chain;
- **On-Invoice Front Discount**: Base wholesale invoice price;
- **Off-Invoice Deductions**: Performance retro-rebates, annual bonuses, slotting allowances, and DC logistics delivery costs;
- **Net-Net Realized Price & True Contribution Margin %**: Exact bottom-line cash retained by the vendor.

### 2. "Give-Get" Concession Balancing Engine (EBITDA Guard — P1)
Protects vendor profitability by transforming one-sided concessions into balanced counter-trades:
- **The Give (Buyer Concession Request)**:
  - Payment Deferral (+Days);
  - Extra Price Discount (+%);
  - Increased Retro-Rebate (+%);
  - Free Logistics / Delivery Absorption;
  - Marketing / Slotting Fee.
- **Monetary Damage Quantification**: Calculates direct cash cost and margin erosion in percentage points.
- **The 4 Compensatory Gets (Keeping Deals EBITDA-Neutral)**:
  1. **Contracted Volume Lift**: Minimum units and percentage surge required so unit contribution margin absorbs the concession;
  2. **Base Price Indexation**: Required price list adjustment if volume remains fixed;
  3. **Rebate Clawback**: Offsetting deduction from quarterly marketing or promotional funds;
  4. **Payment Acceleration**: Upfront cash prepayment requirement to eliminate working capital carry cost.
- **Instant KAM Negotiation Script**: Generates a professional negotiation talk track in English and Russian with 1-tap clipboard copying.

### 3. Net Working Capital (NWC), Cash Conversion Cycle & Factoring Arbitrage (P2)
A dedicated treasury and working capital suite for enterprise contracts:
- **Cash Conversion Cycle (CCC)**: Tracks cash flow velocity across days:
  $$CCC = DSO + DIO - DPO$$
- **Net Working Capital (NWC) Decomposition**:
  $$AR = \text{Revenue} \times \frac{DSO}{365}, \quad Inv = \text{COGS} \times \frac{DIO}{365}, \quad AP = \text{COGS} \times \frac{DPO}{365}$$
  $$NWC = AR + Inv - AP$$
- **Invoice Factoring Engine**:
  - Advance Rate % (presets for 80%, 85%, 90%);
  - Factoring Service Fee % and annual factoring discount rate %;
  - Calculates immediate Day-0 cash inflow, total factoring cost, and Effective APR %;
  - **Required Price Uplift**: Calculates the exact compensatory markup (+% to contract price) needed to neutralize factoring costs.
- **Financial Arbitrage Verdict (Factoring vs Overdraft)**:
  - Compares total factoring fees against revolving bank credit lines;
  - Highlights winning strategy badge (*"Factoring Saves $X vs Bank Overdraft! Instant Liquidity"* or *"Bank Overdraft is Cheaper by $Y"*);
  - Strategic guidance for negotiating when bank credit lines are constrained.

### 4. Tornado Sensitivity & Profit Leverage Chart (McKinsey / DuPont — P4)
Evaluates contract EBITDA vulnerability to independent shifts ($\pm 5\%$ and $\pm 10\%$) in 5 operational drivers:
- **DuPont Profit Leverage Multipliers**: Quantifies the percentage shift in EBITDA resulting from a 1% independent variation in Selling Price ($P$), Sales Volume ($Q$), Direct COGS ($C$), Operating Expenses ($F$), and Rebates ($D$);
- **Interactive Bilateral Tornado Chart**: Center-aligned baseline EBITDA bar chart showing upside/downside ranges sorted in descending order of sensitivity;
- **Price vs Volume Leverage Ratio**: Proves mathematically why defending shelf price is typically $2.0\text{x}$–$4.0\text{x}$ more powerful than pursuing volume concessions;
- **Expandable KAM Negotiation Playbook**: Strategic rules for countering buyer price-slashing tactics.

### 5. Commercial Financing, Floating Rates & Two-Tier Grace Amortization
Structures capital equipment leasing, vendor financing, and long-term installments:
- **5 Payment Frequencies**: Weekly (52/yr), Bi-Weekly (26/yr), Monthly (12/yr), Quarterly (4/yr), and Annually (1/yr);
- **Continuous Uncapped Schedule**: Generates full period-by-period schedules (up to 20–30 years / 240–360 periods) without artificial truncation;
- **Two-Tier Grace Period Amortization**:
  - Subsidized or 0% interest rate during moratorium periods ($1 \dots N_{grace}$);
  - Standard annuity amortization over the remaining tenure ($N_{grace}+1 \dots N_{total}$);
- **Custom Installment & Early Repayment Modeling**:
  - Direct input of custom monthly payment amounts both during the grace moratorium and standard repayment phase;
  - Excess payment directly amortizes principal during grace, calculating exact interest saved (`interestSaved`) and shortened loan tenure (`periodsSaved`);
  - Actuarial true Effective APR (IRR) calculation via Bisection method;
- **Floating Rate Loan Modeling (Benchmark + Spread)**:
  - Models floating benchmark index (SOFR, RUONIA, Central Bank Key Rate) plus bank spread;
  - Configurable rate reset intervals (quarterly, semi-annually, annually) with annual rate trend shifts;
  - Recalculates periodic PMT on outstanding principal on reset dates;
- **Credit Prolongation Analyzer**: Evaluates month-to-month and week-to-week term extensions, contrasting installment relief (-47%) against total interest inflation (+96%);
- **Multi-Currency Debt Basket with Time-Horizon Compounding**:
  - Distributes principal across up to 4 currencies (USD, EUR, CNY, RUB, etc.) with forward currency hedging adjustments;
  - Time-scaled FX compounding: $(1 + \text{FX Shift})^{tenureYears} - 1$;
  - Annual compound capitalization for multi-year facilities ($>1$ year).

### 6. Deal Approval Desk & Traffic Light Governance Corridor (BATNA)
Automated commercial governance before signatures are exchanged:
- 🟢 **Profitable Deal**: Healthy Net-Net margin exceeding corporate WACC/hurdle rates. Eligible for KAM self-approval;
- 🟡 **Borderline Deal**: Margin compressed by rebates or prolonged deferral (>45 days). Commercial Director review required with mandatory counter-trades;
- 🔴 **Margin Destroyer**: Below unit cost or financing charge turns deal toxic. Automated sign-off freeze with CFO escalation;
- **BATNA Corridor**: 1-click presets for Target, Compromise, and Walk-Away boundaries.

### 7. Import Landed Cost & Incoterms Modeler (FOB ➔ Warehouse)
Converts factory pricing into landed domestic warehouse cost:
- Coefficient multipliers ($K \approx 1.28$ to $1.75$) incorporating freight, customs tariffs, broker fees, and import VAT;
- Built-in trade profiles for major hubs: China, Turkey, UAE, European Union, India, and EAEU;
- **Reverse Solver**: Derives maximum allowable FOB factory purchase price from a target domestic landed cost budget.

### 8. Promo Break-Even & Elasticity Lift Solver
Answers the fundamental retail promotion question: *«By what percentage must sales volume increase under a discount of $d\%$, so company gross profit does not decrease?»*
$$\text{Volume Lift}\% = \frac{d\%}{\text{Gross Margin}\% - d\%} \times 100\%$$
Includes feasibility flags to prevent sales teams from signing margin-negative promotions.

### 9. Retail SLA Penalty & Fill-Rate Sensitivity
Simulates net pocket margin after supermarket logistics deductions:
- Calculates underdelivery fines based on planned vs actual order Fill-Rate (80%–100%);
- Models time-slot arrival penalties and pallet quantum non-compliance;
- Exposes actual "Pocket Margin" versus theoretical contract margin.

### 10. Client Negotiation History & Account CRM (Vault)
Enterprise counterparty database with multi-device Firestore cloud synchronization (`users/{uid}/clients/{clientId}`):
- Tracks agreed discount caps, volume tiers, payment deferrals, and currency settings;
- Historical contract log and chronological multi-round negotiation ledger;
- 1-click **"Apply Client Terms"** workflow into active deal calculations;
- Full CRUD operations with real-time Firestore snapshot sync and safe offline fallback.

### 11. Boardroom 2-Page Vector PDF Deal Proposal & LinkedIn Product Flyer
Generates publication-quality vector PDF documents on-device via native `android.graphics.pdf.PdfDocument`:
- **Page 1: Commercial Deal Desk Brief**:
  - Executive header with deal ID, counterparty, and currency;
  - 4-Pillar KPI Cards (Contract Value, Margin %, Payback, LTV:CAC);
  - Deal Approval & Scoring traffic light;
  - Give-Get Concession Balancing breakdown;
  - Tornado Sensitivity table;
  - Retail Price Waterfall cascade.
- **Page 2: Financial Underwriting & Projections**:
  - Financing terms & repayment schedule with Grace Period indicators;
  - Net Working Capital (NWC), Cash Conversion Cycle, and Factoring vs Overdraft summary;
  - Full Contract P&L Statement (Revenue, COGS, Gross Profit, OPEX, EBITDA);
  - Unit Economics & LTV/CAC cohort metrics;
  - Import Landed Cost analysis;
  - 3-signature executive sign-off block (Commercial Director, Head of Legal, CFO).
- **LinkedIn Product Flyer (PDF)**: High-impact executive one-pager showcasing core modules, UI highlights, and CFO ROI metrics for LinkedIn outreach.

### 12. Boardroom-Grade Excel & CSV Spreadsheet Export (UTF-8 BOM)
Native on-device spreadsheet generator with UTF-8 BOM (`\uFEFF`) and RFC 4180 escaping for seamless opening in Microsoft Excel and Google Sheets:
- **Active Deal Underwriting Model**:
  1. *Executive Metrics & Approval*: Net-Net Price, Margin %, Margin Erosion, Cost of Deferral, Risk Score;
  2. *Net Working Capital (NWC) & Factoring Arbitrage P2*: DSO, DIO, DPO, CCC, AR/Inv/AP, advance rate, factoring fee, effective APR, and bank overdraft comparison;
  3. *Contract P&L & Break-Even*: Gross Revenue, COGS, OPEX, EBITDA, Net Profit, ROI %, and Break-Even units and revenue;
  4. *Financing Amortization Schedule*: Complete continuous schedule of periods, installment payments, principal repaid, interest paid, and remaining balance.
- **Deal Vault Portfolio Registry**: One-click portfolio export with total value, weighted margin, and individual contract terms.
- Native Android Share Sheet integration to dispatch directly to Google Drive, Telegram, WhatsApp, Gmail, or MS Excel.

### 13. Dual-Core AI Deal Risk Express Audit
1-tap deal audit button on the top toolbar:
- **Cloud Engine**: Google Gemini 3.8 Flash via Firebase AI Logic;
- **Deterministic Offline Engine (0ms)**: Instant heuristic financial audit providing 100% uptime in basements, flights, or air-gapped meeting rooms;
- **Structured 3-Thesis Output**: Margin traps, Working capital risk, KAM counter-offer playbook;
- **Zero-Failure Guarantee**: Automatically falls back to local engine if network fails.

### 14. Multimodal Commercial Document OCR Scanner (Gemini 3.8 Flash)
1-tap AI camera & gallery scanner in the top toolbar and Price Waterfall header:
- **Direct Document Digitization**: Extracts commercial terms from camera snapshots or Android Photo Picker gallery photos (contracts, specifications, purchase orders, invoices, and discount agreements);
- **Structured JSON Schema Extraction**: Accurately recognizes Shelf RSP, VAT rate %, Retailer Margin %, Front On-Invoice Discount %, Back Retro-Rebates %, Logistics per unit, Supplier COGS, Payment Deferral Days, Volume Quantum, and Currency;
- **1-Click Deal Desk Transfer**: Injects all extracted commercial parameters directly into the Price Waterfall, Deal Approval Scoring, and Payment Terms engines without manual data entry.

### 15. Plan-Fact Variance Bridge & EBITDA Deviation Analysis (P&L Screen)
Deconstructs contract performance variance between planned underwriting and actual execution:
- **5-Factor Variance Decomposition**:
  $$\Delta \text{Net Profit} = \text{Volume Effect} + \text{Price Effect} + \text{Cost Effect} + \text{DSO Deferral Drag} - \text{Penalties}$$
  - **Volume Effect**: $(Q_{fact} - Q_{plan}) \times (P_{plan} - C_{plan})$
  - **Price Effect**: $(P_{fact} - P_{plan}) \times Q_{fact}$
  - **Cost / COGS Effect**: $(C_{plan} - C_{fact}) \times Q_{fact}$
  - **DSO Deferral / Working Capital Drag**: $-(CostOfCarry_{fact} - CostOfCarry_{plan})$
  - **Penalties & Claims**: Direct contract deduction impact.
- **Mathematical Exactness**: The sum of all 5 factors is rigorously identical to the change in net profit ($\Delta \text{Net Profit}$) without fudge factors;
- **Traffic Light Deal Health**: Automated classification into *Outperforming*, *Acceptable Variance*, and *Critical Margin Erosion* with bilingual executive commentary;
- **1-Click Deal Desk Interoperability**: Instant import of planned baseline from the active deal (`importPlanFromCurrentDeal()`) and push of actual contract results into P&L (`applyFactToPnl()`).

---

## Technical Architecture, Security & Code Quality

```
com.example
├── di
│   └── AppContainer.kt          # Lightweight manual DI factory (zero Hilt/KSP build overhead)
├── data
│   ├── ai                       # AiDealRiskAuditor & CommercialOcrScanner (Firebase AI Logic + 0ms fallback)
│   ├── model                    # Immutable Kotlin Data Models (SavedDeal, ClientNegotiationProfile, Amortization)
│   └── repository               # AuthRepository (Credential Manager), DealRepository & ClientNegotiationRepository (Firestore)
├── domain
│   ├── calculator               # Pure Domain Math Engines (CashGap, GiveGet, Tornado, Waterfall, PnL, Amortization, PlanFact)
│   └── model                    # Financial Formula Glossary (Offline Definitions & KAM Tips)
├── service                      # Export Engines: PdfExportService (2-Page Vector PDF) & CsvExportService (UTF-8 BOM)
├── util                         # AppCheckUtil, NumberUtils (localized decimals), PdfImageLoader (URI guard)
└── ui
    ├── components               # Modular Material 3 Cards (PlanFactVarianceCard, CommercialOcrDialog, Tornado, GiveGet)
    ├── screens                  # 6 Root Screens (Margin, Financing, Pipeline, Landed, Vault, Analytics)
    ├── theme                    # Obsidian Executive Dark & Nordic Light Slate (Dynamic Tokens)
    └── viewmodel                # Thin BigDealViewModel facade + DealSessionState & 5 Feature Controllers/Delegates
        ├── DealSessionState         # Active deal context, currency conversion, theme/language/privacy state & event bus
        ├── FinancingController      # Loan amortization, grace periods, early prepayment, multi-currency debt basket
        ├── PricingMarginController  # FOB/Landed Cost, Margin/Markup Solver, Price Waterfall & Promo Break-Even
        ├── WorkingCapitalController # Funnel/LTV, P&L/ROI, Tornado, Payment Terms, Cash Gap, FX Stress & Hurdle ROIC
        ├── KamAndVaultController    # Plan-Fact, KAM Corridor/SLA, Give-Get, Commercial Analyst, Clients & Deal Vault
        └── AiAndExportDelegate      # Gemini Express Risk Audit, Commercial OCR Scanner & PDF/CSV Export orchestration
```

- **Modular Clean MVVM & Manual DI**: `AppContainer` wires repositories and AI delegates once; `BigDealViewModel` acts as a thin UI-facing facade over 6 focused StateHolders while keeping pure math engines in `domain/calculator/` completely decoupled from Android SDK dependencies;
- **Zero-Trust Cloud Sync & Security Hardening**:
  - Owner-isolated schema-validated Firestore rules (`users/{uid}/deals` and `users/{uid}/clients`) backed by Google Sign-In (`CredentialManager`) and Firebase App Check;
  - `FLAG_SECURE` executive Screen Privacy toggle blocking screenshots and recent-app previews;
  - Release-build PII log redaction, OCR numeric bounds sanitization, and CSV formula-injection neutralization;
- **Resilient Localized Number Input**: Custom `toCleanDouble()` and `toCleanInt()` parsers seamlessly handling comma-based decimal inputs (`12,5`) and spaces across 100+ numeric fields;
- **Material Design 3 (M3) Dual-Theming**:
  - **Obsidian Executive Dark**: Deep midnight palette (`#0B101D`) with emerald accents (`#10B981`) designed to reduce visual fatigue during evening sessions;
  - **Nordic Light Slate**: Soft glare-free paper palette (`#F4F6F9`) with crisp slate typography (`#0F172A`) for boardroom projectors and daylight;
- **Zero Third-Party PDF/Excel Bloat**: Pure native vector canvas and byte-stream CSV generation with zero external heavy libraries, zero memory leaks, and native `ACTION_SEND` intent sharing;
- **Comprehensive Automated Testing**: 86 JVM unit tests covering financial math, controllers, amortization, Tornado sensitivities, factoring arbitrage, OCR sanitization, and CSV exports, plus 6 Firestore emulator security rule tests.

---

## Release, Installation & Build

### Signed Final Release (`v1.4.2`)
The production release (`NotaBigDeal-v1.4.2.apk`) is signed with release keys via GitHub Actions, verified on a physical Android device, and hardened for deployment:
- **Download**: Get **`NotaBigDeal-v1.4.2.apk`** from **[GitHub Release v1.4.2](https://github.com/smokingjohnny13-rgb/b2b-financial-calculator/releases/tag/v1.4.2)**;
- **App Directory & Reviews**: Official page on **[AlternativeTo](https://alternativeto.net/software/notabigdeal/)**;
- **CI/CD Pipeline**: Automated release workflow (`.github/workflows/build-apk.yml`) triggered on `v*` / `V*` tags builds a key-signed release APK (`assembleRelease`);
- **Release Hardening**: R8/ProGuard code obfuscation and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), Firebase App Check integration, release-build PII log redaction, and `FLAG_SECURE` executive privacy lock.

### Prerequisites
- Android Studio Ladybug / Meerkat or newer;
- JDK 17+ (JDK 21 used in CI);
- Android SDK 26+ (`minSdk = 26`, `targetSdk = 35`, `compileSdk = 36`).

### Clone & Build
```bash
git clone https://github.com/smokingjohnny13-rgb/b2b-financial-calculator.git
cd b2b-financial-calculator

# Debug APK
gradle assembleDebug

# Signed Release APK / App Bundle (requires release.jks + RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD env vars)
gradle assembleRelease
gradle bundleRelease
```

### Running Automated Verification Tests
Execute the JVM test suite and Firestore security rule tests:
```bash
gradle :app:testDebugUnitTest
firebase emulators:exec --only firestore "node --test firestore.test.js"
```
*(86 JVM unit tests + 6 Firestore rule tests, 100% passing)*.

---

## Dual-Licensing & Copyright

**NotaBigDeal** is dual-licensed to protect its financial architecture, calculation engines, and workflows:

1. **Open Source License — GNU Affero General Public License v3.0 (`AGPL-3.0-or-later`)**:
   - Available for open-source usage, study, and modification under the terms of [`LICENSE`](LICENSE) (see also [`NOTICE`](NOTICE)).
   - Any modified derivative software — including network-accessible services, SaaS deployments, or modified mobile distributions (Section 13) — must disclose the complete Corresponding Source under AGPLv3.
2. **Commercial / Enterprise License**:
   - Required for proprietary/closed-source integration, OEM/white-label distribution, or corporate deployment without copyleft source-disclosure obligations.

---

## Author & Contact

Developed for commercial directors, enterprise Key Account Managers, and financial analysts.

- **GitHub Repository & Release (`v1.4.2`)**: [github.com/smokingjohnny13-rgb/b2b-financial-calculator/releases/tag/v1.4.2](https://github.com/smokingjohnny13-rgb/b2b-financial-calculator/releases/tag/v1.4.2)
- **AlternativeTo (Download & Community Page)**: [alternativeto.net/software/notabigdeal/](https://alternativeto.net/software/notabigdeal/)
- **LinkedIn (Commercial Licensing & Inquiries)**: [www.linkedin.com/in/snitkor](https://www.linkedin.com/in/snitkor)
- **App Name**: **NotaBigDeal** (`v1.4.2` Signed Release)
- **License**: Dual-Licensed — [GNU AGPLv3](LICENSE) / Commercial License (see [NOTICE](NOTICE))

---

## Frequently Asked Questions (FAQ)

### How to calculate net-net margin and payment delay cost offline on Android?
**NotaBigDeal** runs its entire pricing waterfall and working capital engine locally on Android with zero internet required:
- **Net-Net Margin Example (with Back-Bonus / Retro-Rebate)**:
  Suppose **Shelf Price (RSP with 20% VAT)** = `$120.00`, **Retailer Margin** = `30%`, **Front On-Invoice Discount** = `5%`, **Off-Invoice Back-Bonus (Retro-Rebate)** = `8%`, **DC Logistics** = `$3.50/unit`, and **Supplier COGS** = `$40.00`:
  1. Ex-VAT Shelf Price: `$120.00 / 1.20 = $100.00`
  2. After 30% Retailer Margin: `$100.00 - $30.00 = $70.00`
  3. After 5% Front Discount: `$70.00 - $3.50 = $66.50` *(On-Invoice Price)*
  4. After 8% Back-Bonus (`$66.50 × 8% = $5.32`) and `$3.50` Logistics: **`$57.68` Net-Net Realized Price**
  5. Gross Profit: `$57.68 - $40.00 = $17.68` (**`30.65%` true Net-Net Margin** / `44.20%` Markup).
- **Payment Delay (Deferral) Cost Example**:
  On a **`$100,000`** invoice with **60-day payment terms** at an **18% annual cost of capital**:
  $$\text{Deferral Cost} = \$100,000 \times 18\% \times \frac{60}{365} = \$2,958.90 \quad (\text{a hidden } \mathbf{2.96\%} \text{ price cut, or } \$49.32/\text{day})$$
  Offering a **2% early-payment cash discount** (`$98,000` on Day 0) beats waiting 60 days (`$97,126.13` NPV) by **`+$873.87`**.

### Is there a free offline alternative to CPQ calculators for field sales?
Yes. **NotaBigDeal** is a free, open-source (**AGPLv3**) offline-first Android alternative to heavy desktop spreadsheets and cloud enterprise CPQ tools. Commercial directors, field sales reps, and Key Account Managers (KAMs) can calculate deal P&L, Net-Net waterfalls, Give-Get trade-offs, import landed cost (`FOB -> Warehouse`), and export 2-page vector PDF proposals or Excel/CSV models directly on the phone during live negotiations — even in basements or flights without connectivity.

### How much extra volume or price uplift offsets an extra discount or back-rebate?
When a buyer demands an additional **`5%`** discount or retro-rebate on a **`30%`** baseline gross margin contract, the required volume lift to protect gross profit is:
$$\text{Volume Lift}\% = \frac{5\%}{30\% - 5\%} \times 100\% = \mathbf{+20.0\%}$$
Likewise, hitting a **30% Gross Margin** requires a **42.86% Markup** on COGS (`30% / (100% - 30%)`), not 30%. NotaBigDeal's **Give-Get**, **Margin vs Markup**, and **Promo Break-Even** modules solve these trade-offs automatically and generate ready-to-use negotiation scripts.

### Can I compare invoice factoring vs bank overdraft and audit contract terms with AI?
Yes. The **Cash Gap & Factoring Arbitrage** engine calculates Net Working Capital (`NWC = AR + Inv - AP`), Cash Conversion Cycle (`CCC = DSO + DIO - DPO`), effective factoring APR, and the exact compensatory price uplift needed, showing whether factoring beats a bank overdraft. For contract risk checks, the top-bar **AI Risk Audit** and **Document OCR Scanner** work with **Gemini 3.8 Flash** online and fall back seamlessly to a **0 ms deterministic offline risk engine** when offline.
