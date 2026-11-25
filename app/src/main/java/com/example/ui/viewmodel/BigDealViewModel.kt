// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 NotaBigDeal (www.linkedin.com/in/snitkor) — Dual-Licensed: AGPLv3 or Commercial
package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiDealRiskAuditor
import com.example.data.ai.CommercialOcrScanner
import com.example.data.ai.OcrExtractedDealData
import com.example.data.model.AmortizationScheme
import com.example.data.model.AmortizationSummary
import com.example.data.model.CargoCategory
import com.example.data.model.ClientNegotiationProfile
import com.example.data.model.CountryTradeProfile
import com.example.data.model.CurrencyRate
import com.example.data.model.DealApprovalStatus
import com.example.data.model.DealScoringResult
import com.example.data.model.FunnelSummary
import com.example.data.model.LandedCostResult
import com.example.data.model.LtvMetrics
import com.example.data.model.MarginMarkupState
import com.example.data.model.PaymentFrequency
import com.example.data.model.PnlModel
import com.example.data.model.SavedDeal
import com.example.data.repository.AuthRepository
import com.example.data.repository.ClientNegotiationRepository
import com.example.data.repository.DealRepository
import com.example.domain.calculator.BasketSkuItem
import com.example.domain.calculator.BlendedBasketResult
import com.example.domain.calculator.CashGapResult
import com.example.domain.calculator.CurrencyEngine
import com.example.domain.calculator.FxStressTestResult
import com.example.domain.calculator.GiveConcessionType
import com.example.domain.calculator.GiveGetResult
import com.example.domain.calculator.HurdleRoicResult
import com.example.domain.calculator.KamCorridorResult
import com.example.domain.calculator.KamOnePagerSummary
import com.example.domain.calculator.KamScenarioType
import com.example.domain.calculator.PaymentTermsResult
import com.example.domain.calculator.PlanFactResult
import com.example.domain.calculator.PriceWaterfallResult
import com.example.domain.calculator.PromoBreakEvenResult
import com.example.domain.calculator.RebateTierSimulationResult
import com.example.domain.calculator.RetailSlaPenaltyResult
import com.example.domain.calculator.RoiPnlForecast
import com.example.domain.calculator.TornadoAnalysisResult
import com.example.domain.calculator.TradeOffArbitrageResult
import com.example.ui.components.AiAuditUiState
import com.example.ui.i18n.AppLanguage
import com.example.util.toCleanDouble
import com.example.util.toCleanInt
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Thin ViewModel facade composing feature-level StateHolder controllers
 * so all screens and cards keep a single stable entry point without business-logic bloat.
 */
class BigDealViewModel(
    private val dealRepository: DealRepository,
    private val authRepository: AuthRepository,
    private val clientRepository: ClientNegotiationRepository = ClientNegotiationRepository(),
    aiRiskAuditor: AiDealRiskAuditor = AiDealRiskAuditor(),
    commercialOcrScanner: CommercialOcrScanner = CommercialOcrScanner()
) : ViewModel() {

    val session = DealSessionState(viewModelScope)
    val financing = FinancingController(session)
    val pricing = PricingMarginController(session)
    val workingCapital = WorkingCapitalController(
        session = session,
        dealDiscountPctProvider = { kamVault.dealApprovalDiscountPct.value.toCleanDouble() ?: 5.0 }
    )
    val kamVault: KamAndVaultController = KamAndVaultController(
        scope = viewModelScope,
        session = session,
        dealRepository = dealRepository,
        clientRepository = clientRepository,
        fxStressProvider = { workingCapital.fxStressResult.value },
        cashGapProvider = { workingCapital.cashGapResult.value },
        hurdleRateProvider = { workingCapital.hurdleCorporateRate.value.toCleanDouble() ?: 20.0 }
    )
    val aiExport = AiAndExportDelegate(
        scope = viewModelScope,
        session = session,
        aiRiskAuditor = aiRiskAuditor,
        commercialOcrScanner = commercialOcrScanner
    )

    // --- Auth & Cloud Sync ---
    val currentUser: StateFlow<FirebaseUser?> = authRepository.currentUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), authRepository.currentUser)
    val savedDeals: StateFlow<List<SavedDeal>> get() = kamVault.savedDeals
    val isSyncing: StateFlow<Boolean> get() = kamVault.isSyncing
    val userMessage: SharedFlow<String> get() = session.userMessage

    // --- Session Metadata, Theme, Language & Currencies ---
    val dealTitle: MutableStateFlow<String> get() = session.dealTitle
    val clientName: MutableStateFlow<String> get() = session.clientName
    val selectedCurrency: MutableStateFlow<String> get() = session.selectedCurrency
    val appLanguage: MutableStateFlow<AppLanguage> get() = session.appLanguage
    val isDarkTheme: MutableStateFlow<Boolean> get() = session.isDarkTheme
    val isPrivacyModeEnabled: MutableStateFlow<Boolean> get() = session.isPrivacyModeEnabled
    val activeCurrency: MutableStateFlow<CurrencyRate> get() = session.activeCurrency
    val customCurrencies: MutableStateFlow<List<CurrencyRate>> get() = session.customCurrencies
    val currencyRates: MutableStateFlow<List<CurrencyRate>> get() = session.currencyRates
    val targetCurrency: MutableStateFlow<String> get() = session.targetCurrency
    val convertedDealValue: MutableStateFlow<Double> get() = session.convertedDealValue
    val currentCrossRate: MutableStateFlow<Double> get() = session.currentCrossRate

    // --- Financing & Multi-Currency Credit ---
    val principalAmount get() = financing.principalAmount
    val downPayment get() = financing.downPayment
    val annualInterestRate get() = financing.annualInterestRate
    val tenureMonths get() = financing.tenureMonths
    val paymentFrequency get() = financing.paymentFrequency
    val isGracePeriodEnabled get() = financing.isGracePeriodEnabled
    val graceTenureMonths get() = financing.graceTenureMonths
    val graceInterestRate get() = financing.graceInterestRate
    val postGraceInterestRate get() = financing.postGraceInterestRate
    val customMonthlyPayment get() = financing.customMonthlyPayment
    val amortizationScheme get() = financing.amortizationScheme
    val amortization: StateFlow<AmortizationSummary> get() = financing.amortization

    val creditPrincipal get() = financing.creditPrincipal
    val creditTenureMonths get() = financing.creditTenureMonths
    val creditSelectedCurrencies get() = financing.creditSelectedCurrencies
    val creditHedgeEnabled get() = financing.creditHedgeEnabled
    val creditHedgeCostPct get() = financing.creditHedgeCostPct
    val creditExpectedFxShiftPct get() = financing.creditExpectedFxShiftPct
    val creditCurrencyRates get() = financing.creditCurrencyRates
    val creditAllocations get() = financing.creditAllocations
    val multiCreditResult get() = financing.multiCreditResult

    // --- Landed Cost, Margin, Waterfall & Promo ---
    val selectedCategory get() = pricing.selectedCategory
    val selectedCountry get() = pricing.selectedCountry
    val fobPriceInput get() = pricing.fobPriceInput
    val targetLandedCostInput get() = pricing.targetLandedCostInput
    val expressCoeffInput get() = pricing.expressCoeffInput
    val isLandedReverseMode get() = pricing.isLandedReverseMode
    val customLogisticsPct get() = pricing.customLogisticsPct
    val customDutyPct get() = pricing.customDutyPct
    val customVatPct get() = pricing.customVatPct
    val customBrokerPct get() = pricing.customBrokerPct
    val landedCostResult: StateFlow<LandedCostResult> get() = pricing.landedCostResult

    val marginCostInput get() = pricing.marginCostInput
    val marginPriceInput get() = pricing.marginPriceInput
    val marginState: StateFlow<MarginMarkupState> get() = pricing.marginState

    val waterfallRsp get() = pricing.waterfallRsp
    val waterfallVat get() = pricing.waterfallVat
    val waterfallRetailerMargin get() = pricing.waterfallRetailerMargin
    val waterfallFrontDiscount get() = pricing.waterfallFrontDiscount
    val waterfallBackBonus get() = pricing.waterfallBackBonus
    val waterfallLogistics get() = pricing.waterfallLogistics
    val waterfallCogs get() = pricing.waterfallCogs
    val waterfallResult: StateFlow<PriceWaterfallResult> get() = pricing.waterfallResult

    val promoBasePrice get() = pricing.promoBasePrice
    val promoUnitCost get() = pricing.promoUnitCost
    val promoDiscountPct get() = pricing.promoDiscountPct
    val promoBaseVolume get() = pricing.promoBaseVolume
    val promoSimulatedLift get() = pricing.promoSimulatedLift
    val promoResult: StateFlow<PromoBreakEvenResult> get() = pricing.promoResult

    // --- SaaS Funnel, P&L, Tornado, Terms, Cash Gap, FX Stress & Hurdle ROIC ---
    val arpu get() = workingCapital.arpu
    val grossMarginPct get() = workingCapital.grossMarginPct
    val churnRatePct get() = workingCapital.churnRatePct
    val cac get() = workingCapital.cac
    val ltvMetrics: StateFlow<LtvMetrics> get() = workingCapital.ltvMetrics

    val funnelLeads get() = workingCapital.funnelLeads
    val funnelMqls get() = workingCapital.funnelMqls
    val funnelSqls get() = workingCapital.funnelSqls
    val funnelOpps get() = workingCapital.funnelOpps
    val funnelClosedWon get() = workingCapital.funnelClosedWon
    val avgDealValue get() = workingCapital.avgDealValue
    val funnelSummary: StateFlow<FunnelSummary> get() = workingCapital.funnelSummary

    val pnlRevenue get() = workingCapital.pnlRevenue
    val pnlCogs get() = workingCapital.pnlCogs
    val pnlOpex get() = workingCapital.pnlOpex
    val pnlInvestment get() = workingCapital.pnlInvestment
    val pnlDurationMonths get() = workingCapital.pnlDurationMonths
    val fixedCosts get() = workingCapital.fixedCosts
    val unitPrice get() = workingCapital.unitPrice
    val unitCost get() = workingCapital.unitCost
    val pnlModel: StateFlow<PnlModel> get() = workingCapital.pnlModel

    val tornadoDeltaPct get() = workingCapital.tornadoDeltaPct
    val tornadoResult: StateFlow<TornadoAnalysisResult> get() = workingCapital.tornadoResult

    val termsDealAmount get() = workingCapital.termsDealAmount
    val termsDays get() = workingCapital.termsDays
    val termsAnnualRate get() = workingCapital.termsAnnualRate
    val termsCashDiscount get() = workingCapital.termsCashDiscount
    val paymentTermsResult: StateFlow<PaymentTermsResult> get() = workingCapital.paymentTermsResult

    val cashGapRevenue get() = workingCapital.cashGapRevenue
    val cashGapCogs get() = workingCapital.cashGapCogs
    val cashGapPrepaidPct get() = workingCapital.cashGapPrepaidPct
    val cashGapSupplierDays get() = workingCapital.cashGapSupplierDays
    val cashGapCustomerDays get() = workingCapital.cashGapCustomerDays
    val cashGapInventoryDays get() = workingCapital.cashGapInventoryDays
    val cashGapOverdraftRate get() = workingCapital.cashGapOverdraftRate
    val cashGapFactoringAdvancePct get() = workingCapital.cashGapFactoringAdvancePct
    val cashGapFactoringFeePct get() = workingCapital.cashGapFactoringFeePct
    val cashGapFactoringRatePct get() = workingCapital.cashGapFactoringRatePct
    val cashGapResult: StateFlow<CashGapResult> get() = workingCapital.cashGapResult

    val fxContractPrice get() = workingCapital.fxContractPrice
    val fxCogsForeign get() = workingCapital.fxCogsForeign
    val fxForeignCurrency get() = workingCapital.fxForeignCurrency
    val fxSpotRate get() = workingCapital.fxSpotRate
    val fxHedgedPct get() = workingCapital.fxHedgedPct
    val fxHedgeRate get() = workingCapital.fxHedgeRate
    val fxDomesticCost get() = workingCapital.fxDomesticCost
    val fxStressResult: StateFlow<FxStressTestResult> get() = workingCapital.fxStressResult

    val hurdleDealRevenue get() = workingCapital.hurdleDealRevenue
    val hurdleNetProfit get() = workingCapital.hurdleNetProfit
    val hurdleTiedUpCapital get() = workingCapital.hurdleTiedUpCapital
    val hurdleCycleDays get() = workingCapital.hurdleCycleDays
    val hurdleCorporateRate get() = workingCapital.hurdleCorporateRate
    val hurdleRoicResult: StateFlow<HurdleRoicResult> get() = workingCapital.hurdleRoicResult

    // --- Plan-Fact, KAM Corridor, Give-Get, Commercial Analyst, Deal Scoring & Clients ---
    val planFactPlanPrice get() = kamVault.planFactPlanPrice
    val planFactPlanVolume get() = kamVault.planFactPlanVolume
    val planFactPlanCost get() = kamVault.planFactPlanCost
    val planFactPlanDays get() = kamVault.planFactPlanDays
    val planFactActualPrice get() = kamVault.planFactActualPrice
    val planFactActualVolume get() = kamVault.planFactActualVolume
    val planFactActualCost get() = kamVault.planFactActualCost
    val planFactActualDays get() = kamVault.planFactActualDays
    val planFactActualPenalties get() = kamVault.planFactActualPenalties
    val planFactCapitalRate get() = kamVault.planFactCapitalRate
    val planFactResult: StateFlow<PlanFactResult> get() = kamVault.planFactResult

    val kamBaseShelfPrice get() = kamVault.kamBaseShelfPrice
    val kamBaseCogs get() = kamVault.kamBaseCogs
    val kamActiveScenarioType get() = kamVault.kamActiveScenarioType
    val kamCorridorResult: StateFlow<KamCorridorResult> get() = kamVault.kamCorridorResult

    val kamDealAmount get() = kamVault.kamDealAmount
    val kamFillRatePct get() = kamVault.kamFillRatePct
    val kamPenaltyRatePct get() = kamVault.kamPenaltyRatePct
    val kamTimeSlotPenalty get() = kamVault.kamTimeSlotPenalty
    val kamSlaResult: StateFlow<RetailSlaPenaltyResult> get() = kamVault.kamSlaResult

    val kamOnePagerSummary: StateFlow<KamOnePagerSummary> get() = kamVault.kamOnePagerSummary
    val showKamOnePagerDialog get() = kamVault.showKamOnePagerDialog

    val giveConcessionType get() = kamVault.giveConcessionType
    val giveConcessionValue get() = kamVault.giveConcessionValue
    val giveGetResult: StateFlow<GiveGetResult> get() = kamVault.giveGetResult

    val tradeOffDealAmount get() = kamVault.tradeOffDealAmount
    val tradeOffDiscountPct get() = kamVault.tradeOffDiscountPct
    val tradeOffDeferralDays get() = kamVault.tradeOffDeferralDays
    val tradeOffAnnualRate get() = kamVault.tradeOffAnnualRate
    val tradeOffResult: StateFlow<TradeOffArbitrageResult> get() = kamVault.tradeOffResult

    val basketItems: StateFlow<List<BasketSkuItem>> get() = kamVault.basketItems
    val blendedBasketResult: StateFlow<BlendedBasketResult> get() = kamVault.blendedBasketResult
    val rebateActualVolume get() = kamVault.rebateActualVolume
    val rebateBaseMarginPct get() = kamVault.rebateBaseMarginPct
    val rebateSimulationResult: StateFlow<RebateTierSimulationResult> get() = kamVault.rebateSimulationResult

    val clientsList: StateFlow<List<ClientNegotiationProfile>> get() = kamVault.clientsList
    val selectedClientId get() = kamVault.selectedClientId
    val selectedClient: StateFlow<ClientNegotiationProfile?> get() = kamVault.selectedClient

    val dealApprovalSellingPrice get() = kamVault.dealApprovalSellingPrice
    val dealApprovalCostPrice get() = kamVault.dealApprovalCostPrice
    val dealApprovalVolumeUnits get() = kamVault.dealApprovalVolumeUnits
    val dealApprovalDiscountPct get() = kamVault.dealApprovalDiscountPct
    val dealApprovalBonusPct get() = kamVault.dealApprovalBonusPct
    val dealApprovalPaymentDays get() = kamVault.dealApprovalPaymentDays
    val dealApprovalFinancingRate get() = kamVault.dealApprovalFinancingRate
    val dealApprovalStatus get() = kamVault.dealApprovalStatus
    val dealScoringResult: StateFlow<DealScoringResult> get() = kamVault.dealScoringResult

    // --- AI Audit & Commercial OCR ---
    val aiAuditState: StateFlow<AiAuditUiState> get() = aiExport.aiAuditState
    val isOcrScanning: MutableStateFlow<Boolean> get() = aiExport.isOcrScanning
    val ocrScanResult: MutableStateFlow<OcrExtractedDealData?> get() = aiExport.ocrScanResult
    val ocrError: MutableStateFlow<String?> get() = aiExport.ocrError
    val isOcrDialogOpen: MutableStateFlow<Boolean> get() = aiExport.isOcrDialogOpen

    init {
        viewModelScope.launch {
            authRepository.currentUserFlow.collect { user ->
                if (user != null) {
                    dealRepository.startFirestoreSync(viewModelScope)
                    clientRepository.startFirestoreSync(viewModelScope)
                } else {
                    dealRepository.stopFirestoreSync()
                    clientRepository.stopFirestoreSync()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dealRepository.stopFirestoreSync()
        clientRepository.stopFirestoreSync()
    }

    // --- Session & Currency Actions ---
    fun toggleLanguage() {
        session.appLanguage.value = session.appLanguage.value.toggle()
        recalculateDealScoring()
    }

    fun setLanguage(lang: AppLanguage) {
        session.appLanguage.value = lang
        recalculateDealScoring()
    }

    fun toggleTheme() = session.toggleTheme()
    fun setPrivacyMode(enabled: Boolean) = session.setPrivacyMode(enabled)

    fun setTargetCurrency(code: String) =
        session.setTargetCurrency(code, principalAmount.value.toCleanDouble() ?: 0.0)

    fun updateCurrencyConversion() =
        session.updateCurrencyConversion(principalAmount.value.toCleanDouble() ?: 0.0)

    fun selectActiveCurrency(code: String) =
        session.selectActiveCurrency(code, principalAmount.value.toCleanDouble() ?: 0.0)

    fun addCustomCurrency(code: String, name: String, symbol: String, rateToBaseUsd: Double) =
        session.addCustomCurrency(code, name, symbol, rateToBaseUsd, principalAmount.value.toCleanDouble() ?: 0.0)

    fun formatCurrency(amount: Double): String = session.formatCurrency(amount)

    // --- Financing & Multi-Currency Actions ---
    fun updateFinancing() = financing.updateFinancing()
    fun setAmortizationScheme(scheme: AmortizationScheme) = financing.setAmortizationScheme(scheme)
    fun setCustomMonthlyPayment(value: String) = financing.setCustomMonthlyPayment(value)
    fun clearCustomMonthlyPayment() = financing.clearCustomMonthlyPayment()
    fun toggleGracePeriod(enabled: Boolean) = financing.toggleGracePeriod(enabled)
    fun setPaymentFrequency(freq: PaymentFrequency) = financing.setPaymentFrequency(freq)

    fun toggleCreditCurrency(code: String) = financing.toggleCreditCurrency(code)
    fun setCreditCurrenciesCount(count: Int) = financing.setCreditCurrenciesCount(count)
    fun updateCreditCurrencyRate(code: String, rate: Double) = financing.updateCreditCurrencyRate(code, rate)
    fun updateCreditAllocation(code: String, alloc: Double) = financing.updateCreditAllocation(code, alloc)
    fun setCreditHedgeEnabled(enabled: Boolean) = financing.setCreditHedgeEnabled(enabled)
    fun updateMultiCreditCalculations() = financing.updateMultiCreditCalculations()

    fun importDealToMultiCredit() {
        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        val sp = dealApprovalSellingPrice.value.toCleanDouble() ?: 100.0
        val p = if (sp > 0 && vol > 0) sp * vol else (principalAmount.value.toCleanDouble() ?: 120000.0)
        val t = tenureMonths.value.toCleanInt() ?: 12
        creditPrincipal.value = session.formatCleanNum(p)
        creditTenureMonths.value = t.toString()
        updateMultiCreditCalculations()
        session.emitLocalized(
            "Deal data imported to multi-currency credit calculator: ${formatCurrency(p)}",
            "Данные сделки импортированы в мультивалютный кредитный калькулятор: ${formatCurrency(p)}"
        )
    }

    fun applyCreditHedgedRateToFinancing() {
        val hedgedRate = multiCreditResult.value.weightedEffectiveRateWithHedgePct
        val rateFormatted = String.format(Locale.US, "%.1f", hedgedRate)
        annualInterestRate.value = rateFormatted
        updateFinancing()
        dealApprovalFinancingRate.value = rateFormatted
        recalculateDealScoring()
        session.emitLocalized(
            "Deal financing rate updated: ${annualInterestRate.value}% (basket & hedge adjusted)",
            "Ставка финансирования сделки обновлена: ${annualInterestRate.value}% (с учетом корзины и хеджа)"
        )
    }

    // --- Landed Cost, Margin, Waterfall & Promo Actions ---
    fun updateLandedCostCalculations() = pricing.updateLandedCostCalculations()
    fun setLandedReverseMode(reverse: Boolean) = pricing.setLandedReverseMode(reverse)
    fun applyCargoCategory(category: CargoCategory) = pricing.applyCargoCategory(category)
    fun applyCountryTradeProfile(country: CountryTradeProfile) = pricing.applyCountryTradeProfile(country)
    fun recalculateCustomCoefficient() = pricing.recalculateCustomCoefficient()
    fun calibrateCoefficientFromPrices() = pricing.calibrateCoefficientFromPrices()
    fun transferLandedCostToMarginSolver() = pricing.transferLandedCostToMarginSolver()
    fun applyFobLandedCostToMargin(landedCost: Double) = pricing.applyFobLandedCostToMargin(landedCost)

    fun transferLandedCostToDeal() {
        val landed = landedCostResult.value.landedCost
        val costStr = session.formatCleanNum(landed)
        dealApprovalCostPrice.value = costStr

        val currentPrice = dealApprovalSellingPrice.value.toCleanDouble() ?: 100.0
        if (currentPrice <= landed) {
            val suggestedPrice = landed / (1.0 - 0.35)
            dealApprovalSellingPrice.value = session.formatCleanNum(suggestedPrice)
        }
        recalculateDealScoring()

        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        val sp = dealApprovalSellingPrice.value.toCleanDouble() ?: (landed * 1.54)
        val totalDealValue = sp * vol
        principalAmount.value = session.formatCleanNum(totalDealValue)
        updateFinancing()

        unitCost.value = costStr
        unitPrice.value = dealApprovalSellingPrice.value
        pnlCogs.value = session.formatCleanNum(landed * vol)
        pnlRevenue.value = session.formatCleanNum(totalDealValue)
        updatePnlCalculations()

        marginCostInput.value = costStr
        marginPriceInput.value = dealApprovalSellingPrice.value
        onMarginCostOrPriceChanged()

        session.emitLocalized(
            "Deal COGS updated with Landed Cost: ${formatCurrency(landed)}",
            "Себестоимость сделки (COGS) обновлена из Landed Cost: ${formatCurrency(landed)}"
        )
    }

    fun onMarginCostOrPriceChanged(newCost: String? = null, newPrice: String? = null) =
        pricing.onMarginCostOrPriceChanged(newCost, newPrice)
    fun applyMarginPercentPreset(marginPct: Double) = pricing.applyMarginPercentPreset(marginPct)
    fun applyMarkupPercentPreset(markupPct: Double) = pricing.applyMarkupPercentPreset(markupPct)

    fun updateWaterfallCalculations() = pricing.updateWaterfallCalculations()
    fun importCogsFromLandedCost() = pricing.importCogsFromLandedCost()

    fun applyWaterfallToMarginSolver() {
        val net = waterfallResult.value.netNetPrice
        val cogs = waterfallResult.value.supplierCogs
        val costStr = session.formatCleanNum(cogs)
        val netStr = session.formatCleanNum(net)
        onMarginCostOrPriceChanged(newCost = costStr, newPrice = netStr)
        dealApprovalCostPrice.value = costStr
        dealApprovalSellingPrice.value = if (waterfallRsp.value.isNotBlank()) waterfallRsp.value else netStr
        dealApprovalDiscountPct.value = waterfallFrontDiscount.value
        dealApprovalBonusPct.value = waterfallBackBonus.value
        recalculateDealScoring()
        session.emitLocalized(
            "Net-Net price (${formatCurrency(net)}) transferred to margin solver and deal desk",
            "Чистая цена (${formatCurrency(net)}) передана в расчет маржи и сделку"
        )
    }

    fun applyWaterfallToPnl() {
        val net = waterfallResult.value.netNetPrice
        val cogs = waterfallResult.value.supplierCogs
        unitPrice.value = session.formatCleanNum(net)
        unitCost.value = session.formatCleanNum(cogs)
        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        pnlRevenue.value = session.formatCleanNum(net * vol)
        pnlCogs.value = session.formatCleanNum(cogs * vol)
        updatePnlCalculations()
        session.emitLocalized(
            "Net-Net price and cost transferred to P&L / Break-Even",
            "Net-Net цена и себестоимость переданы в P&L / Точку безубыточности"
        )
    }

    fun updatePromoCalculations() = pricing.updatePromoCalculations()
    fun importPromoFromWaterfall() = pricing.importPromoFromWaterfall()

    // --- SaaS Funnel, P&L, Terms, Cash Gap, FX Stress & Hurdle ROIC Actions ---
    fun updateLtvCalculations() = workingCapital.updateLtvCalculations()
    fun updateFunnelCalculations() = workingCapital.updateFunnelCalculations()
    fun updatePnlCalculations() = workingCapital.updatePnlCalculations()
    fun setTornadoDeltaPct(pct: Double) = workingCapital.setTornadoDeltaPct(pct)
    fun updateTornadoCalculations() = workingCapital.updateTornadoCalculations()
    fun applyRoiPnlForecast(forecast: RoiPnlForecast) = workingCapital.applyRoiPnlForecast(forecast)

    fun updatePaymentTermsCalculations() = workingCapital.updatePaymentTermsCalculations()

    fun importTermsFromDeal() {
        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        val sp = dealApprovalSellingPrice.value.toCleanDouble() ?: 100.0
        val dealTotal = if (sp > 0 && vol > 0) sp * vol else (principalAmount.value.toCleanDouble() ?: 120000.0)
        termsDealAmount.value = session.formatCleanNum(dealTotal)
        termsDays.value = dealApprovalPaymentDays.value
        termsAnnualRate.value = dealApprovalFinancingRate.value
        updatePaymentTermsCalculations()
        session.emitLocalized(
            "Deal terms imported (Amount: ${formatCurrency(dealTotal)}, Days: ${termsDays.value}, Rate: ${termsAnnualRate.value}%)",
            "Условия сделки импортированы (Сумма: ${formatCurrency(dealTotal)}, Отсрочка: ${termsDays.value} дн., Ставка: ${termsAnnualRate.value}%)"
        )
    }

    fun applyEffectiveNetToDeal() {
        val npv = paymentTermsResult.value.netPresentValue
        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        if (vol > 0) {
            dealApprovalSellingPrice.value = session.formatCleanNum(npv / vol)
            recalculateDealScoring()
        }
        val npvStr = session.formatCleanNum(npv)
        principalAmount.value = npvStr
        updateFinancing()
        pnlRevenue.value = npvStr
        updatePnlCalculations()
        session.emitLocalized(
            "Deal amount adjusted for terms cost (NPV): ${formatCurrency(npv)}",
            "Сумма сделки скорректирована на стоимость отсрочки (NPV): ${formatCurrency(npv)}"
        )
    }

    fun updateCashGapCalculations() = workingCapital.updateCashGapCalculations()

    fun importDealToCashGap() {
        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        val sp = dealApprovalSellingPrice.value.toCleanDouble() ?: 100.0
        val cp = dealApprovalCostPrice.value.toCleanDouble() ?: 55.0
        val rev = if (sp > 0 && vol > 0) sp * vol else (principalAmount.value.toCleanDouble() ?: 100000.0)
        val realCogs = if (cp > 0 && vol > 0) cp * vol else (rev * (1.0 - (marginState.value.marginPct / 100.0)))

        cashGapRevenue.value = session.formatCleanNum(rev)
        cashGapCogs.value = session.formatCleanNum(realCogs)
        cashGapCustomerDays.value = dealApprovalPaymentDays.value
        cashGapOverdraftRate.value = dealApprovalFinancingRate.value
        updateCashGapCalculations()
        session.emitLocalized(
            "Deal imported to Cash Gap (Revenue: ${formatCurrency(rev)}, COGS: ${formatCurrency(realCogs)}, DSO: ${cashGapCustomerDays.value}d)",
            "Сделка импортирована в кассовый разрыв (Выручка: ${formatCurrency(rev)}, Себестоимость: ${formatCurrency(realCogs)}, Отсрочка: ${cashGapCustomerDays.value} дн.)"
        )
    }

    fun updateFxStressCalculations() = workingCapital.updateFxStressCalculations()
    fun setFxStressForeignCurrency(curr: String) = workingCapital.setFxStressForeignCurrency(curr)
    fun updateHurdleRoicCalculations() = workingCapital.updateHurdleRoicCalculations()

    fun importDealToHurdleRoic() {
        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        val sp = dealApprovalSellingPrice.value.toCleanDouble() ?: 100.0
        val cp = dealApprovalCostPrice.value.toCleanDouble() ?: 55.0
        val rev = if (sp > 0 && vol > 0) sp * vol else (principalAmount.value.toCleanDouble() ?: 100000.0)
        val tiedUp = if (cp > 0 && vol > 0) cp * vol else (rev * 0.65)
        val scoringProfit = dealScoringResult.value.totalNetNetProfit
        val netProfit = if (scoringProfit > 0.0) scoringProfit else (rev * (marginState.value.marginPct / 100.0))

        hurdleDealRevenue.value = session.formatCleanNum(rev)
        hurdleNetProfit.value = session.formatCleanNum(netProfit)
        hurdleTiedUpCapital.value = session.formatCleanNum(tiedUp)
        hurdleCycleDays.value = dealApprovalPaymentDays.value
        updateHurdleRoicCalculations()
        session.emitLocalized(
            "Deal synced to ROIC Hurdle Guardrail (Capital: ${formatCurrency(tiedUp)}, Profit: ${formatCurrency(netProfit)}, Cycle: ${hurdleCycleDays.value}d)",
            "Сделка синхронизирована с ROIC (Капитал: ${formatCurrency(tiedUp)}, Прибыль: ${formatCurrency(netProfit)}, Цикл: ${hurdleCycleDays.value} дн.)"
        )
    }

    // --- Plan-Fact, KAM Corridor, Give-Get, Commercial Analyst, Clients & Vault Actions ---
    fun updatePlanFactCalculations() = kamVault.updatePlanFactCalculations()

    fun importPlanFromCurrentDeal() {
        val sp = dealApprovalSellingPrice.value.toCleanDouble() ?: 100.0
        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        val cp = dealApprovalCostPrice.value.toCleanDouble() ?: 60.0
        val days = dealApprovalPaymentDays.value.toCleanInt() ?: 30
        val rate = dealApprovalFinancingRate.value.toCleanDouble() ?: 18.0

        planFactPlanPrice.value = session.formatCleanNum(sp)
        planFactPlanVolume.value = vol.toString()
        planFactPlanCost.value = session.formatCleanNum(cp)
        planFactPlanDays.value = days.toString()
        planFactCapitalRate.value = rate.toString()

        if ((planFactActualPrice.value.toCleanDouble() ?: 0.0) <= 0.0) {
            planFactActualPrice.value = planFactPlanPrice.value
            planFactActualVolume.value = planFactPlanVolume.value
            planFactActualCost.value = planFactPlanCost.value
            planFactActualDays.value = planFactPlanDays.value
        }

        updatePlanFactCalculations()
        session.emitLocalized(
            "Plan imported from Deal Desk (Price: ${formatCurrency(sp)}, Volume: $vol pcs, Cost: ${formatCurrency(cp)})",
            "План импортирован из сделки (Цена: ${formatCurrency(sp)}, Объем: $vol шт, Себестоимость: ${formatCurrency(cp)})"
        )
    }

    fun applyFactToPnl() {
        val ap = planFactActualPrice.value.toCleanDouble() ?: 100.0
        val av = planFactActualVolume.value.toCleanInt() ?: 1000
        val ac = planFactActualCost.value.toCleanDouble() ?: 60.0
        val totalRev = ap * av
        val totalCogs = ac * av

        pnlRevenue.value = session.formatCleanNum(totalRev)
        pnlCogs.value = session.formatCleanNum(totalCogs)
        unitPrice.value = session.formatCleanNum(ap)
        unitCost.value = session.formatCleanNum(ac)
        updatePnlCalculations()
        session.emitLocalized(
            "Actual performance (${formatCurrency(totalRev)}) transferred to main P&L model",
            "Фактические результаты (${formatCurrency(totalRev)}) перенесены в P&L"
        )
    }

    fun updateKamCorridor() = kamVault.updateKamCorridor()
    fun selectKamScenario(type: KamScenarioType) = kamVault.selectKamScenario(type)
    fun updateKamSla() = kamVault.updateKamSla()
    fun updateKamOnePager() = kamVault.updateKamOnePager()
    fun toggleKamOnePager(show: Boolean) = kamVault.toggleKamOnePager(show)

    fun applyKamScenarioToDeal() {
        val scenario = kamCorridorResult.value.activeScenario
        val costStr = session.formatCleanNum(scenario.cogs)
        val netStr = session.formatCleanNum(scenario.netNetPrice)
        val shelfStr = session.formatCleanNum(scenario.shelfPrice)
        onMarginCostOrPriceChanged(newCost = costStr, newPrice = netStr)

        dealApprovalCostPrice.value = costStr
        dealApprovalSellingPrice.value = shelfStr
        dealApprovalDiscountPct.value = scenario.frontDiscountPct.toString()
        dealApprovalBonusPct.value = scenario.backBonusPct.toString()
        dealApprovalPaymentDays.value = scenario.paymentDays.toString()
        recalculateDealScoring()

        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        principalAmount.value = session.formatCleanNum(scenario.netNetPrice * vol)
        updateFinancing()

        session.emitLocalized(
            "Applied '${scenario.nameEn}' scenario to deal pricing & desk",
            "Применен сценарий '${scenario.nameRu}' к сделке и согласованию"
        )
    }

    fun setGiveConcessionType(type: GiveConcessionType) = kamVault.setGiveConcessionType(type)
    fun setGiveConcessionValue(value: String) = kamVault.setGiveConcessionValue(value)
    fun updateGiveGetCalculation() = kamVault.updateGiveGetCalculation()

    fun updateTradeOff() = kamVault.updateTradeOff()
    fun updateBasketItem(id: String, newVolume: Double, newPrice: Double, newCogs: Double) =
        kamVault.updateBasketItem(id, newVolume, newPrice, newCogs)
    fun resetBasketToDefaults() = kamVault.resetBasketToDefaults()
    fun updateRebateSimulation() = kamVault.updateRebateSimulation()

    fun applyTradeOffToTerms() {
        val days = tradeOffDeferralDays.value.toCleanInt() ?: 30
        val deal = tradeOffDealAmount.value.toCleanDouble() ?: 100000.0
        val rate = tradeOffAnnualRate.value.toCleanDouble() ?: 20.0
        termsDays.value = days.toString()
        termsDealAmount.value = session.formatCleanNum(deal)
        termsAnnualRate.value = rate.toString()
        updatePaymentTermsCalculations()

        dealApprovalPaymentDays.value = days.toString()
        dealApprovalFinancingRate.value = rate.toString()
        recalculateDealScoring()
        session.emitLocalized(
            "Trade-off terms transferred to Payment Terms and Deal (Days: $days, Rate: $rate%)",
            "Условия отсрочки перенесены в условия платежа и сделку (Дней: $days, Ставка: $rate%)"
        )
    }

    fun applyBasketMarginToDeal() {
        val blendedMargin = blendedBasketResult.value.blendedMarginPct
        applyMarginPercentPreset(blendedMargin)

        val cp = dealApprovalCostPrice.value.toCleanDouble() ?: 55.0
        if (blendedMargin in 0.01..99.99) {
            val newPrice = cp / (1.0 - blendedMargin / 100.0)
            dealApprovalSellingPrice.value = session.formatCleanNum(newPrice)
            recalculateDealScoring()
        }
        val pctLabel = String.format(Locale.US, "%.1f", blendedMargin)
        session.emitLocalized(
            "Blended basket margin ($pctLabel%) applied to Margin Solver and Deal",
            "Средневзвешенная маржа корзины ($pctLabel%) применена к расчету маржи и сделке"
        )
    }

    fun recalculateDealScoring() = kamVault.recalculateDealScoring()
    fun submitDealForApproval() = kamVault.submitDealForApproval()
    fun approveDeal() = kamVault.approveDeal()
    fun rejectDeal(reason: String = "") = kamVault.rejectDeal(reason)
    fun selectClient(clientId: String) = kamVault.selectClient(clientId)
    fun saveClientProfile(profile: ClientNegotiationProfile, isNew: Boolean) =
        kamVault.saveClientProfile(profile, isNew)
    fun deleteClientProfile(clientId: String) = kamVault.deleteClientProfile(clientId)
    fun addNegotiationRound(
        clientId: String,
        buyerDemands: String,
        kamCounter: String,
        discountPct: Double,
        bonusPct: Double,
        termsDays: Int,
        outcome: String
    ) = kamVault.addNegotiationRound(clientId, buyerDemands, kamCounter, discountPct, bonusPct, termsDays, outcome)

    fun saveCurrentDeal() {
        kamVault.saveDealScenario(
            principal = principalAmount.value.toCleanDouble() ?: 120000.0,
            interestRatePct = annualInterestRate.value.toCleanDouble() ?: 6.5,
            tenureMonths = tenureMonths.value.toCleanInt() ?: 24,
            frequency = paymentFrequency.value,
            periodicPayment = amortization.value.periodicPayment,
            ltv = ltvMetrics.value.ltv,
            ltvCacRatio = ltvMetrics.value.ltvCacRatio,
            marginPct = marginState.value.marginPct,
            markupPct = marginState.value.markupPct,
            roiPct = pnlModel.value.roiPct,
            paybackMonths = pnlModel.value.paybackMonths
        )
    }

    fun loadDeal(deal: SavedDeal) {
        dealTitle.value = deal.title
        clientName.value = deal.clientName
        selectedCurrency.value = deal.currencyCode
        activeCurrency.value = CurrencyEngine.findByCode(deal.currencyCode, customCurrencies.value)
        principalAmount.value = session.formatCleanNum(deal.dealValue)
        annualInterestRate.value = deal.interestRatePct.toString()
        tenureMonths.value = deal.tenureMonths.toString()
        paymentFrequency.value = deal.frequency

        updateFinancing()
        onMarginCostOrPriceChanged(
            newCost = session.formatCleanNum(deal.dealValue * (1.0 - deal.marginPct / 100.0)),
            newPrice = session.formatCleanNum(deal.dealValue)
        )
        pnlRevenue.value = session.formatCleanNum(deal.dealValue)
        updatePnlCalculations()

        val vol = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000
        val unitP = if (vol > 0 && deal.dealValue > 0) deal.dealValue / vol else deal.dealValue
        val unitC = unitP * (1.0 - deal.marginPct / 100.0)
        dealApprovalSellingPrice.value = session.formatCleanNum(unitP)
        dealApprovalCostPrice.value = session.formatCleanNum(unitC)
        recalculateDealScoring()

        if (deal.hasPlanFactData) {
            planFactPlanPrice.value = session.formatCleanNum(deal.planFactPlanPrice)
            planFactPlanVolume.value = deal.planFactPlanVolume.toString()
            planFactPlanCost.value = session.formatCleanNum(deal.planFactPlanCost)
            planFactPlanDays.value = deal.planFactPlanDays.toString()
            planFactActualPrice.value = session.formatCleanNum(deal.planFactActualPrice)
            planFactActualVolume.value = deal.planFactActualVolume.toString()
            planFactActualCost.value = session.formatCleanNum(deal.planFactActualCost)
            planFactActualDays.value = deal.planFactActualDays.toString()
            planFactActualPenalties.value = session.formatCleanNum(deal.planFactActualPenalties)
            planFactCapitalRate.value = session.formatCleanNum(deal.planFactCapitalRate)
        } else {
            val priceStr = session.formatCleanNum(unitP)
            val costStr = session.formatCleanNum(unitC)
            planFactPlanPrice.value = priceStr
            planFactPlanVolume.value = vol.toString()
            planFactPlanCost.value = costStr
            planFactActualPrice.value = priceStr
            planFactActualVolume.value = vol.toString()
            planFactActualCost.value = costStr
            planFactActualPenalties.value = "0"
        }
        updatePlanFactCalculations()

        session.emitLocalized(
            "Loaded scenario: ${deal.title}",
            "Загружен сценарий: ${deal.title}"
        )
    }

    fun deleteDeal(dealId: String) = kamVault.deleteDeal(dealId)

    // --- Export, Auth & AI / OCR ---
    private fun buildActiveSavedDeal(): SavedDeal {
        val currentDealValue = (dealApprovalSellingPrice.value.toCleanDouble() ?: 100.0) *
            (dealApprovalVolumeUnits.value.toCleanInt() ?: 1000)
        return SavedDeal(
            title = dealTitle.value,
            clientName = clientName.value,
            currencyCode = activeCurrency.value.code,
            dealValue = if (currentDealValue > 0.0) currentDealValue else (principalAmount.value.toCleanDouble() ?: 120000.0),
            interestRatePct = annualInterestRate.value.toCleanDouble() ?: 6.5,
            tenureMonths = tenureMonths.value.toCleanInt() ?: 24,
            frequency = paymentFrequency.value,
            periodicPayment = amortization.value.periodicPayment,
            ltv = ltvMetrics.value.ltv,
            ltvCacRatio = ltvMetrics.value.ltvCacRatio,
            marginPct = marginState.value.marginPct,
            markupPct = marginState.value.markupPct,
            roiPct = pnlModel.value.roiPct,
            paybackMonths = pnlModel.value.paybackMonths,
            updatedAt = System.currentTimeMillis()
        )
    }

    fun exportPdf(context: Context) {
        aiExport.exportPdf(
            context = context,
            deal = buildActiveSavedDeal(),
            amortization = amortization.value,
            pnl = pnlModel.value,
            ltv = ltvMetrics.value,
            margin = marginState.value,
            landedCost = landedCostResult.value,
            dealScoring = dealScoringResult.value,
            dealStatus = dealApprovalStatus.value,
            giveGet = giveGetResult.value,
            tornado = tornadoResult.value,
            waterfall = waterfallResult.value,
            cashGap = cashGapResult.value,
            planFact = planFactResult.value
        )
    }

    fun exportProductFlyerPdf(context: Context) = aiExport.exportProductFlyerPdf(context)

    fun exportDealCsv(context: Context) {
        aiExport.exportDealCsv(
            context = context,
            deal = buildActiveSavedDeal(),
            scoring = dealScoringResult.value,
            cashGap = cashGapResult.value,
            pnl = pnlModel.value,
            amortization = amortization.value,
            planFact = planFactResult.value
        )
    }

    fun exportVaultRegistryCsv(context: Context) =
        aiExport.exportVaultRegistryCsv(context, savedDeals.value)

    fun signInWithGoogle(activityContext: Context) {
        viewModelScope.launch {
            val result = authRepository.signInWithGoogle(activityContext)
            result.onSuccess { user ->
                session.emitMessage("Connected as ${user.displayName ?: user.email}")
                dealRepository.startFirestoreSync(viewModelScope)
                clientRepository.startFirestoreSync(viewModelScope)
            }.onFailure { e ->
                session.emitMessage("Sign-in cancelled or failed: ${e.message}")
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                clientRepository.stopFirestoreSync()
                dealRepository.resetForSignOut()
                clientRepository.resetToDefaults()
                clientRepository.refreshFirestoreInstanceAfterReset()
                session.resetSessionMetadata()
                kamVault.resetApprovalSession()
                aiExport.resetSessionState()
                authRepository.signOut()
            }
            session.emitLocalized(
                "Signed out and session cache cleared",
                "Выход выполнен, данные сессии очищены"
            )
        }
    }

    fun resetAiAuditState() = aiExport.resetAiAuditState()

    fun runExpressAudit(forceLocal: Boolean = false) {
        aiExport.runExpressAudit(
            margin = marginState.value,
            waterfall = waterfallResult.value,
            terms = paymentTermsResult.value,
            cashGap = cashGapResult.value,
            promo = promoResult.value,
            forceLocal = forceLocal
        )
    }

    fun runLocalAudit() = runExpressAudit(forceLocal = true)
    fun openOcrDialog() = aiExport.openOcrDialog()
    fun closeOcrDialog() = aiExport.closeOcrDialog()
    fun scanDocumentBitmap(bitmap: Bitmap) = aiExport.scanDocumentBitmap(bitmap)

    fun applyOcrExtractedData(rawData: OcrExtractedDealData) {
        val data = CommercialOcrScanner.sanitizeOcrResult(rawData)
        if (data.clientName.isNotBlank()) {
            clientName.value = data.clientName
        }
        session.syncCurrencyIfKnown(data.currencyCode)
        data.shelfPriceRsp?.let {
            val s = session.formatCleanNum(it)
            waterfallRsp.value = s
            dealApprovalSellingPrice.value = s
        }
        data.vatRatePct?.let {
            waterfallVat.value = if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.1f", it)
        }
        data.retailerMarginPct?.let {
            waterfallRetailerMargin.value = if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.1f", it)
        }
        data.frontDiscountPct?.let {
            val s = if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.1f", it)
            waterfallFrontDiscount.value = s
            dealApprovalDiscountPct.value = s
        }
        data.backBonusPct?.let {
            val s = if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.US, "%.1f", it)
            waterfallBackBonus.value = s
            dealApprovalBonusPct.value = s
        }
        data.logisticsCostPerUnit?.let {
            waterfallLogistics.value = session.formatCleanNum(it)
        }
        data.supplierCogs?.let {
            val s = session.formatCleanNum(it)
            waterfallCogs.value = s
            dealApprovalCostPrice.value = s
            marginCostInput.value = s
        }
        data.paymentTermsDays?.let {
            dealApprovalPaymentDays.value = it.toString()
            termsDays.value = it.toString()
        }
        data.contractVolumeUnits?.let {
            dealApprovalVolumeUnits.value = it.toString()
            promoBaseVolume.value = it.toString()
        }

        updateWaterfallCalculations()
        recalculateDealScoring()
        updatePaymentTermsCalculations()
        updateCurrencyConversion()
        isOcrDialogOpen.value = false

        session.emitLocalized(
            "OCR terms applied to Deal Desk!",
            "Коммерческие условия OCR применены к сделке!"
        )
    }
}
