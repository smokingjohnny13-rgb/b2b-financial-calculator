package com.example.ui.viewmodel

import com.example.data.model.FunnelSummary
import com.example.data.model.LtvMetrics
import com.example.data.model.PnlModel
import com.example.domain.calculator.CashGapEngine
import com.example.domain.calculator.CashGapResult
import com.example.domain.calculator.FunnelCalculator
import com.example.domain.calculator.FxStressTestEngine
import com.example.domain.calculator.FxStressTestResult
import com.example.domain.calculator.HurdleRoicEngine
import com.example.domain.calculator.HurdleRoicResult
import com.example.domain.calculator.LtvCalculator
import com.example.domain.calculator.PaymentTermsEngine
import com.example.domain.calculator.PaymentTermsResult
import com.example.domain.calculator.PnlRoiEngine
import com.example.domain.calculator.RoiPnlForecast
import com.example.domain.calculator.TornadoAnalysisResult
import com.example.domain.calculator.TornadoSensitivityEngine
import com.example.util.toCleanDouble
import com.example.util.toCleanInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Controller for SaaS Funnel/LTV, Contract P&L / ROI, Tornado Sensitivity,
 * Payment Terms, Cash Gap / Factoring, FX Stress-Test, and Hurdle ROIC.
 */
class WorkingCapitalController(
    private val session: DealSessionState,
    private val dealDiscountPctProvider: () -> Double
) {
    // SaaS & Funnel Economics
    val arpu = MutableStateFlow("2500")
    val grossMarginPct = MutableStateFlow("75")
    val churnRatePct = MutableStateFlow("2.5")
    val cac = MutableStateFlow("4000")
    private val _ltvMetrics = MutableStateFlow(
        LtvCalculator.calculate(2500.0, 75.0, 2.5, 4000.0)
    )
    val ltvMetrics: StateFlow<LtvMetrics> = _ltvMetrics.asStateFlow()

    val funnelLeads = MutableStateFlow("1200")
    val funnelMqls = MutableStateFlow("450")
    val funnelSqls = MutableStateFlow("180")
    val funnelOpps = MutableStateFlow("60")
    val funnelClosedWon = MutableStateFlow("18")
    val avgDealValue = MutableStateFlow("35000")
    private val _funnelSummary = MutableStateFlow(
        FunnelCalculator.calculate(1200, 450, 180, 60, 18, 35000.0)
    )
    val funnelSummary: StateFlow<FunnelSummary> = _funnelSummary.asStateFlow()

    // Analytics (ROI, P&L & Tornado)
    val pnlRevenue = MutableStateFlow("150000")
    val pnlCogs = MutableStateFlow("45000")
    val pnlOpex = MutableStateFlow("35000")
    val pnlInvestment = MutableStateFlow("50000")
    val pnlDurationMonths = MutableStateFlow("12")
    val fixedCosts = MutableStateFlow("25000")
    val unitPrice = MutableStateFlow("500")
    val unitCost = MutableStateFlow("150")
    private val _pnlModel = MutableStateFlow(
        PnlRoiEngine.calculate(
            revenue = 150000.0,
            cogs = 45000.0,
            opex = 35000.0,
            initialInvestment = 50000.0,
            fixedCosts = 25000.0,
            pricePerUnit = 500.0,
            variableCostPerUnit = 150.0,
            dealDurationMonths = 12.0
        )
    )
    val pnlModel: StateFlow<PnlModel> = _pnlModel.asStateFlow()

    val tornadoDeltaPct = MutableStateFlow(5.0)
    private val _tornadoResult = MutableStateFlow(
        TornadoSensitivityEngine.calculate(
            revenue = 150000.0,
            cogs = 45000.0,
            opex = 35000.0,
            deltaPct = 5.0,
            discountPct = 5.0
        )
    )
    val tornadoResult: StateFlow<TornadoAnalysisResult> = _tornadoResult.asStateFlow()

    // Payment Terms
    val termsDealAmount = MutableStateFlow("100000")
    val termsDays = MutableStateFlow("60")
    val termsAnnualRate = MutableStateFlow("18")
    val termsCashDiscount = MutableStateFlow("2")
    private val _paymentTermsResult = MutableStateFlow(
        PaymentTermsEngine.calculate(
            dealAmount = 100000.0,
            paymentDays = 60,
            annualInterestRatePct = 18.0,
            cashDiscountPct = 2.0
        )
    )
    val paymentTermsResult: StateFlow<PaymentTermsResult> = _paymentTermsResult.asStateFlow()

    // Cash Gap & Working Capital Peak
    val cashGapRevenue = MutableStateFlow("100000")
    val cashGapCogs = MutableStateFlow("70000")
    val cashGapPrepaidPct = MutableStateFlow("20")
    val cashGapSupplierDays = MutableStateFlow("30")
    val cashGapCustomerDays = MutableStateFlow("60")
    val cashGapInventoryDays = MutableStateFlow("15")
    val cashGapOverdraftRate = MutableStateFlow("22")
    val cashGapFactoringAdvancePct = MutableStateFlow("85")
    val cashGapFactoringFeePct = MutableStateFlow("1.5")
    val cashGapFactoringRatePct = MutableStateFlow("20")
    private val _cashGapResult = MutableStateFlow(
        CashGapEngine.calculate(
            dealRevenue = 100000.0,
            cogsTotal = 70000.0,
            prepaidPct = 20.0,
            supplierPayDays = 30,
            customerPayDays = 60,
            inventoryDays = 15,
            overdraftRatePct = 22.0
        )
    )
    val cashGapResult: StateFlow<CashGapResult> = _cashGapResult.asStateFlow()

    // FX Stress-Test
    val fxContractPrice = MutableStateFlow("1000000")
    val fxCogsForeign = MutableStateFlow("8000")
    val fxForeignCurrency = MutableStateFlow("USD")
    val fxSpotRate = MutableStateFlow("95.0")
    val fxHedgedPct = MutableStateFlow("30")
    val fxHedgeRate = MutableStateFlow("96.0")
    val fxDomesticCost = MutableStateFlow("50000")
    private val _fxStressResult = MutableStateFlow(
        FxStressTestEngine.calculate(
            contractPrice = 1000000.0,
            cogsForeignAmount = 8000.0,
            foreignCurrencyCode = "USD",
            spotRate = 95.0,
            hedgedPct = 30.0,
            hedgeRate = 96.0,
            domesticCost = 50000.0
        )
    )
    val fxStressResult: StateFlow<FxStressTestResult> = _fxStressResult.asStateFlow()

    // Hurdle Rate & ROIC Guardrail
    val hurdleDealRevenue = MutableStateFlow("100000")
    val hurdleNetProfit = MutableStateFlow("7000")
    val hurdleTiedUpCapital = MutableStateFlow("65000")
    val hurdleCycleDays = MutableStateFlow("90")
    val hurdleCorporateRate = MutableStateFlow("20")
    private val _hurdleRoicResult = MutableStateFlow(
        HurdleRoicEngine.calculate(
            dealRevenue = 100000.0,
            dealNetProfit = 7000.0,
            tiedUpCapital = 65000.0,
            cycleDays = 90,
            corporateHurdleRatePct = 20.0
        )
    )
    val hurdleRoicResult: StateFlow<HurdleRoicResult> = _hurdleRoicResult.asStateFlow()

    fun updateLtvCalculations() {
        _ltvMetrics.value = LtvCalculator.calculate(
            arpu = arpu.value.toCleanDouble() ?: 0.0,
            grossMarginPct = grossMarginPct.value.toCleanDouble() ?: 70.0,
            churnRatePct = churnRatePct.value.toCleanDouble() ?: 2.0,
            cac = cac.value.toCleanDouble() ?: 1000.0
        )
    }

    fun updateFunnelCalculations() {
        _funnelSummary.value = FunnelCalculator.calculate(
            leads = funnelLeads.value.toCleanInt() ?: 0,
            mqls = funnelMqls.value.toCleanInt() ?: 0,
            sqls = funnelSqls.value.toCleanInt() ?: 0,
            opportunities = funnelOpps.value.toCleanInt() ?: 0,
            closedWon = funnelClosedWon.value.toCleanInt() ?: 0,
            avgDealValue = avgDealValue.value.toCleanDouble() ?: 0.0
        )
    }

    fun updatePnlCalculations() {
        val rev = pnlRevenue.value.toCleanDouble() ?: 0.0
        val cogs = pnlCogs.value.toCleanDouble() ?: 0.0
        val opex = pnlOpex.value.toCleanDouble() ?: 0.0
        val inv = pnlInvestment.value.toCleanDouble() ?: 0.0
        val dur = pnlDurationMonths.value.toCleanDouble() ?: 12.0
        val fixed = fixedCosts.value.toCleanDouble() ?: (opex * 0.7)
        val pUnit = unitPrice.value.toCleanDouble() ?: 500.0
        val cUnit = unitCost.value.toCleanDouble() ?: 150.0

        _pnlModel.value = PnlRoiEngine.calculate(
            revenue = rev,
            cogs = cogs,
            opex = opex,
            initialInvestment = inv,
            fixedCosts = fixed,
            pricePerUnit = pUnit,
            variableCostPerUnit = cUnit,
            dealDurationMonths = dur
        )
        updateTornadoCalculations()
    }

    fun setTornadoDeltaPct(pct: Double) {
        tornadoDeltaPct.value = pct
        updateTornadoCalculations()
    }

    fun updateTornadoCalculations() {
        _tornadoResult.value = TornadoSensitivityEngine.calculate(
            revenue = pnlRevenue.value.toCleanDouble() ?: 0.0,
            cogs = pnlCogs.value.toCleanDouble() ?: 0.0,
            opex = pnlOpex.value.toCleanDouble() ?: 0.0,
            deltaPct = tornadoDeltaPct.value,
            discountPct = dealDiscountPctProvider()
        )
    }

    fun applyRoiPnlForecast(forecast: RoiPnlForecast) {
        pnlRevenue.value = session.formatCleanNum(forecast.total12MonthRevenue)
        val cogs = maxOf(0.0, forecast.total12MonthRevenue - forecast.total12MonthGrossProfit)
        pnlCogs.value = session.formatCleanNum(cogs)
        pnlOpex.value = session.formatCleanNum(forecast.total12MonthOpex)
        pnlInvestment.value = session.formatCleanNum(forecast.totalCacInvestment)
        pnlDurationMonths.value = "12"
        updatePnlCalculations()
        session.emitLocalized(
            "Applied 12-Month Cohort Forecast to Contract P&L",
            "Прогноз когорты 12м применен к P&L контракта"
        )
    }

    fun updatePaymentTermsCalculations() {
        _paymentTermsResult.value = PaymentTermsEngine.calculate(
            dealAmount = termsDealAmount.value.toCleanDouble() ?: 100000.0,
            paymentDays = termsDays.value.toCleanInt() ?: 60,
            annualInterestRatePct = termsAnnualRate.value.toCleanDouble() ?: 18.0,
            cashDiscountPct = termsCashDiscount.value.toCleanDouble() ?: 2.0
        )
    }

    fun updateCashGapCalculations() {
        _cashGapResult.value = CashGapEngine.calculate(
            dealRevenue = cashGapRevenue.value.toCleanDouble() ?: 100000.0,
            cogsTotal = cashGapCogs.value.toCleanDouble() ?: 70000.0,
            prepaidPct = cashGapPrepaidPct.value.toCleanDouble() ?: 20.0,
            supplierPayDays = cashGapSupplierDays.value.toCleanInt() ?: 30,
            customerPayDays = cashGapCustomerDays.value.toCleanInt() ?: 60,
            inventoryDays = cashGapInventoryDays.value.toCleanInt() ?: 15,
            overdraftRatePct = cashGapOverdraftRate.value.toCleanDouble() ?: 22.0,
            factoringAdvancePct = cashGapFactoringAdvancePct.value.toCleanDouble() ?: 85.0,
            factoringFeePct = cashGapFactoringFeePct.value.toCleanDouble() ?: 1.5,
            factoringInterestRatePct = cashGapFactoringRatePct.value.toCleanDouble() ?: 20.0
        )
    }

    fun updateFxStressCalculations() {
        _fxStressResult.value = FxStressTestEngine.calculate(
            contractPrice = fxContractPrice.value.toCleanDouble() ?: 1000000.0,
            cogsForeignAmount = fxCogsForeign.value.toCleanDouble() ?: 8000.0,
            foreignCurrencyCode = fxForeignCurrency.value,
            spotRate = fxSpotRate.value.toCleanDouble() ?: 95.0,
            hedgedPct = fxHedgedPct.value.toCleanDouble() ?: 30.0,
            hedgeRate = fxHedgeRate.value.toCleanDouble() ?: 96.0,
            domesticCost = fxDomesticCost.value.toCleanDouble() ?: 50000.0
        )
    }

    fun setFxStressForeignCurrency(curr: String) {
        fxForeignCurrency.value = curr
        val defaultRate = when (curr) {
            "USD" -> 95.0
            "EUR" -> 104.0
            "CNY" -> 13.2
            "BYN" -> 29.5
            "KZT" -> 0.20
            else -> 1.0
        }
        fxSpotRate.value = defaultRate.toString()
        fxHedgeRate.value = String.format(Locale.US, "%.1f", defaultRate * 1.02)
        updateFxStressCalculations()
    }

    fun updateHurdleRoicCalculations() {
        _hurdleRoicResult.value = HurdleRoicEngine.calculate(
            dealRevenue = hurdleDealRevenue.value.toCleanDouble() ?: 100000.0,
            dealNetProfit = hurdleNetProfit.value.toCleanDouble() ?: 7000.0,
            tiedUpCapital = hurdleTiedUpCapital.value.toCleanDouble() ?: 65000.0,
            cycleDays = hurdleCycleDays.value.toCleanInt() ?: 90,
            corporateHurdleRatePct = hurdleCorporateRate.value.toCleanDouble() ?: 20.0
        )
    }
}
