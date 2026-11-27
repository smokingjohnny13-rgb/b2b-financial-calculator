// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 NotaBigDeal (www.linkedin.com/in/snitkor) — Dual-Licensed: AGPLv3 or Commercial
package com.example.data.repository

import android.content.Context
import com.example.data.model.AmortizationScheme
import com.example.data.model.CargoCategory
import com.example.data.model.CurrencyRate
import com.example.data.model.DealApprovalStatus
import com.example.data.model.PaymentFrequency
import com.example.domain.calculator.BasketSkuItem
import com.example.domain.calculator.CurrencyEngine
import com.example.domain.calculator.GiveConcessionType
import com.example.domain.calculator.KamScenarioType
import com.example.domain.calculator.LandedCostCalculator
import com.example.ui.i18n.AppLanguage
import com.example.ui.viewmodel.DealSessionState
import com.example.ui.viewmodel.FinancingController
import com.example.ui.viewmodel.KamAndVaultController
import com.example.ui.viewmodel.PricingMarginController
import com.example.ui.viewmodel.WorkingCapitalController
import java.io.StringReader
import java.io.StringWriter
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Properties

interface DraftStore {
    fun readDraft(): String?
    fun writeDraft(content: String)
    fun clearDraft()
    fun isRememberEnabled(): Boolean
    fun setRememberEnabled(enabled: Boolean)
}

class InMemoryDraftStore(
    initialRememberEnabled: Boolean = true
) : DraftStore {
    private var payload: String? = null
    private var rememberEnabled: Boolean = initialRememberEnabled

    override fun readDraft(): String? = payload
    override fun writeDraft(content: String) {
        payload = content
    }
    override fun clearDraft() {
        payload = null
    }
    override fun isRememberEnabled(): Boolean = rememberEnabled
    override fun setRememberEnabled(enabled: Boolean) {
        rememberEnabled = enabled
    }
}

class SharedPreferencesDraftStore(context: Context) : DraftStore {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readDraft(): String? = prefs.getString(KEY_PAYLOAD, null)

    override fun writeDraft(content: String) {
        prefs.edit().putString(KEY_PAYLOAD, content).apply()
    }

    override fun clearDraft() {
        prefs.edit().remove(KEY_PAYLOAD).apply()
    }

    override fun isRememberEnabled(): Boolean = prefs.getBoolean(KEY_REMEMBER_ENABLED, true)

    override fun setRememberEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMEMBER_ENABLED, enabled).apply()
    }

    private companion object {
        const val PREFS_NAME = "notabigdeal_workspace_draft"
        const val KEY_PAYLOAD = "draft_properties"
        const val KEY_REMEMBER_ENABLED = "remember_last_session"
    }
}

/**
 * Pure-JVM serializable workspace draft manager so pre-meeting inputs, FX rates,
 * active tab, and toggles survive process kills and reopen ready to present.
 */
class WorkspaceDraftStorage(
    private val store: DraftStore = InMemoryDraftStore()
) {
    constructor(context: Context) : this(SharedPreferencesDraftStore(context))

    fun isRememberEnabled(): Boolean = store.isRememberEnabled()

    fun setRememberEnabled(enabled: Boolean) {
        store.setRememberEnabled(enabled)
        if (!enabled) {
            store.clearDraft()
        }
    }

    fun clearDraft() {
        store.clearDraft()
    }

    fun saveSnapshot(
        session: DealSessionState,
        financing: FinancingController,
        pricing: PricingMarginController,
        workingCapital: WorkingCapitalController,
        kamVault: KamAndVaultController? = null
    ) {
        if (!session.isRememberDraftEnabled.value || !store.isRememberEnabled()) return
        val p = Properties()

        // Session & FX
        p["session.tab"] = session.selectedTabIndex.value.toString()
        p["session.dealTitle"] = session.dealTitle.value
        p["session.clientName"] = session.clientName.value
        p["session.selectedCurrency"] = session.selectedCurrency.value
        p["session.targetCurrency"] = session.targetCurrency.value
        p["session.language"] = session.appLanguage.value.name
        p["session.darkTheme"] = session.isDarkTheme.value.toString()
        p["session.privacyMode"] = session.isPrivacyModeEnabled.value.toString()
        p["session.customCurrencies"] = encodeCurrencies(session.customCurrencies.value)
        p["session.fxCountryRegion"] = session.fxCountryRegion.value.name
        p["session.isClientCountryEnabled"] = session.isClientCountryEnabled.value.toString()
        p["session.fxRateMode"] = session.fxRateMode.value.name
        session.fxSelectedPointId.value?.let { p["session.fxSelectedPointId"] = it }
        p["session.isFxAutoBestEnabled"] = session.isFxAutoBestEnabled.value.toString()
        session.fxLastUpdatedAt.value?.let { p["session.fxLastUpdatedAt"] = it.toString() }
        if (session.fxAvailablePoints.value.isNotEmpty()) {
            p["session.fxAvailablePoints"] = encodeFxPoints(session.fxAvailablePoints.value)
        }

        // Financing & Multi-Currency Credit
        p["fin.principal"] = financing.principalAmount.value
        p["fin.downPayment"] = financing.downPayment.value
        p["fin.rate"] = financing.annualInterestRate.value
        p["fin.tenure"] = financing.tenureMonths.value
        p["fin.frequency"] = financing.paymentFrequency.value.name
        p["fin.graceEnabled"] = financing.isGracePeriodEnabled.value.toString()
        p["fin.graceTenure"] = financing.graceTenureMonths.value
        p["fin.graceRate"] = financing.graceInterestRate.value
        p["fin.postGraceRate"] = financing.postGraceInterestRate.value
        p["fin.customMonthly"] = financing.customMonthlyPayment.value
        p["fin.scheme"] = financing.amortizationScheme.value.name

        p["mc.principal"] = financing.creditPrincipal.value
        p["mc.tenure"] = financing.creditTenureMonths.value
        p["mc.currencies"] = financing.creditSelectedCurrencies.value.joinToString(",")
        p["mc.hedgeEnabled"] = financing.creditHedgeEnabled.value.toString()
        p["mc.hedgeCost"] = financing.creditHedgeCostPct.value
        p["mc.fxShift"] = financing.creditExpectedFxShiftPct.value
        p["mc.rates"] = encodeDoubleMap(financing.creditCurrencyRates.value)
        p["mc.allocations"] = encodeDoubleMap(financing.creditAllocations.value)

        // Pricing, Landed Cost, Waterfall & Promo
        p["land.category"] = pricing.selectedCategory.value.name
        p["land.countryCode"] = pricing.selectedCountry.value.code
        p["land.fob"] = pricing.fobPriceInput.value
        p["land.target"] = pricing.targetLandedCostInput.value
        p["land.coeff"] = pricing.expressCoeffInput.value
        p["land.reverse"] = pricing.isLandedReverseMode.value.toString()
        p["land.logPct"] = pricing.customLogisticsPct.value
        p["land.dutyPct"] = pricing.customDutyPct.value
        p["land.vatPct"] = pricing.customVatPct.value
        p["land.brokerPct"] = pricing.customBrokerPct.value

        p["margin.cost"] = pricing.marginCostInput.value
        p["margin.price"] = pricing.marginPriceInput.value

        p["wf.rsp"] = pricing.waterfallRsp.value
        p["wf.vat"] = pricing.waterfallVat.value
        p["wf.retMargin"] = pricing.waterfallRetailerMargin.value
        p["wf.frontDisc"] = pricing.waterfallFrontDiscount.value
        p["wf.backBonus"] = pricing.waterfallBackBonus.value
        p["wf.logistics"] = pricing.waterfallLogistics.value
        p["wf.cogs"] = pricing.waterfallCogs.value

        p["promo.basePrice"] = pricing.promoBasePrice.value
        p["promo.unitCost"] = pricing.promoUnitCost.value
        p["promo.discPct"] = pricing.promoDiscountPct.value
        p["promo.baseVol"] = pricing.promoBaseVolume.value
        p["promo.simLift"] = pricing.promoSimulatedLift.value

        // Working Capital, Funnel, P&L, Payment Terms, Cash Gap, FX Stress & ROIC
        p["ltv.arpu"] = workingCapital.arpu.value
        p["ltv.gm"] = workingCapital.grossMarginPct.value
        p["ltv.churn"] = workingCapital.churnRatePct.value
        p["ltv.cac"] = workingCapital.cac.value

        p["fn.leads"] = workingCapital.funnelLeads.value
        p["fn.mqls"] = workingCapital.funnelMqls.value
        p["fn.sqls"] = workingCapital.funnelSqls.value
        p["fn.opps"] = workingCapital.funnelOpps.value
        p["fn.won"] = workingCapital.funnelClosedWon.value
        p["fn.avgDeal"] = workingCapital.avgDealValue.value

        p["pnl.rev"] = workingCapital.pnlRevenue.value
        p["pnl.cogs"] = workingCapital.pnlCogs.value
        p["pnl.opex"] = workingCapital.pnlOpex.value
        p["pnl.inv"] = workingCapital.pnlInvestment.value
        p["pnl.dur"] = workingCapital.pnlDurationMonths.value
        p["pnl.fixed"] = workingCapital.fixedCosts.value
        p["pnl.unitPrice"] = workingCapital.unitPrice.value
        p["pnl.unitCost"] = workingCapital.unitCost.value
        p["pnl.tornadoDelta"] = workingCapital.tornadoDeltaPct.value.toString()

        p["terms.amt"] = workingCapital.termsDealAmount.value
        p["terms.days"] = workingCapital.termsDays.value
        p["terms.rate"] = workingCapital.termsAnnualRate.value
        p["terms.cashDisc"] = workingCapital.termsCashDiscount.value

        p["cg.rev"] = workingCapital.cashGapRevenue.value
        p["cg.cogs"] = workingCapital.cashGapCogs.value
        p["cg.prepaid"] = workingCapital.cashGapPrepaidPct.value
        p["cg.supDays"] = workingCapital.cashGapSupplierDays.value
        p["cg.custDays"] = workingCapital.cashGapCustomerDays.value
        p["cg.invDays"] = workingCapital.cashGapInventoryDays.value
        p["cg.overdraft"] = workingCapital.cashGapOverdraftRate.value
        p["cg.factAdv"] = workingCapital.cashGapFactoringAdvancePct.value
        p["cg.factFee"] = workingCapital.cashGapFactoringFeePct.value
        p["cg.factRate"] = workingCapital.cashGapFactoringRatePct.value

        p["fxs.price"] = workingCapital.fxContractPrice.value
        p["fxs.cogs"] = workingCapital.fxCogsForeign.value
        p["fxs.curr"] = workingCapital.fxForeignCurrency.value
        p["fxs.spot"] = workingCapital.fxSpotRate.value
        p["fxs.hedgedPct"] = workingCapital.fxHedgedPct.value
        p["fxs.hedgeRate"] = workingCapital.fxHedgeRate.value
        p["fxs.domCost"] = workingCapital.fxDomesticCost.value

        p["roic.rev"] = workingCapital.hurdleDealRevenue.value
        p["roic.profit"] = workingCapital.hurdleNetProfit.value
        p["roic.cap"] = workingCapital.hurdleTiedUpCapital.value
        p["roic.days"] = workingCapital.hurdleCycleDays.value
        p["roic.rate"] = workingCapital.hurdleCorporateRate.value

        // KAM, Plan-Fact, Analyst & Deal Approval
        if (kamVault != null) {
            p["pf.pPrice"] = kamVault.planFactPlanPrice.value
            p["pf.pVol"] = kamVault.planFactPlanVolume.value
            p["pf.pCost"] = kamVault.planFactPlanCost.value
            p["pf.pDays"] = kamVault.planFactPlanDays.value
            p["pf.aPrice"] = kamVault.planFactActualPrice.value
            p["pf.aVol"] = kamVault.planFactActualVolume.value
            p["pf.aCost"] = kamVault.planFactActualCost.value
            p["pf.aDays"] = kamVault.planFactActualDays.value
            p["pf.aPen"] = kamVault.planFactActualPenalties.value
            p["pf.capRate"] = kamVault.planFactCapitalRate.value

            p["kam.shelf"] = kamVault.kamBaseShelfPrice.value
            p["kam.cogs"] = kamVault.kamBaseCogs.value
            p["kam.scenario"] = kamVault.kamActiveScenarioType.value.name
            p["kam.dealAmt"] = kamVault.kamDealAmount.value
            p["kam.fill"] = kamVault.kamFillRatePct.value
            p["kam.pen"] = kamVault.kamPenaltyRatePct.value
            p["kam.slotPen"] = kamVault.kamTimeSlotPenalty.value

            p["gg.type"] = kamVault.giveConcessionType.value.name
            p["gg.val"] = kamVault.giveConcessionValue.value

            p["to.amt"] = kamVault.tradeOffDealAmount.value
            p["to.disc"] = kamVault.tradeOffDiscountPct.value
            p["to.days"] = kamVault.tradeOffDeferralDays.value
            p["to.rate"] = kamVault.tradeOffAnnualRate.value

            p["ca.basket"] = encodeBasket(kamVault.basketItems.value)
            p["ca.rebVol"] = kamVault.rebateActualVolume.value
            p["ca.rebMargin"] = kamVault.rebateBaseMarginPct.value

            kamVault.selectedClientId.value?.let { p["deal.clientId"] = it }
            p["deal.sp"] = kamVault.dealApprovalSellingPrice.value
            p["deal.cp"] = kamVault.dealApprovalCostPrice.value
            p["deal.vol"] = kamVault.dealApprovalVolumeUnits.value
            p["deal.disc"] = kamVault.dealApprovalDiscountPct.value
            p["deal.bonus"] = kamVault.dealApprovalBonusPct.value
            p["deal.days"] = kamVault.dealApprovalPaymentDays.value
            p["deal.rate"] = kamVault.dealApprovalFinancingRate.value
            p["deal.status"] = kamVault.dealApprovalStatus.value.name
        }

        val writer = StringWriter()
        p.store(writer, "NotaBigDealWorkspaceDraft")
        store.writeDraft(writer.toString())
    }

    /**
     * Restores the saved workspace snapshot if remember mode is active and a draft exists.
     * Returns true if a draft was applied.
     */
    fun restoreInto(
        session: DealSessionState,
        financing: FinancingController,
        pricing: PricingMarginController,
        workingCapital: WorkingCapitalController,
        kamVault: KamAndVaultController? = null,
        recalculateAll: () -> Unit = {}
    ): Boolean {
        val enabled = store.isRememberEnabled()
        session.isRememberDraftEnabled.value = enabled
        if (!enabled) return false

        val raw = store.readDraft() ?: return false
        if (raw.isBlank()) return false

        val p = Properties()
        runCatching { p.load(StringReader(raw)) }.getOrElse { return false }
        if (p.isEmpty) return false

        // Session & FX
        p.getProperty("session.tab")?.toIntOrNull()?.let { session.selectedTabIndex.value = it.coerceIn(0, 5) }
        p.getProperty("session.dealTitle")?.let { session.dealTitle.value = it }
        p.getProperty("session.clientName")?.let { session.clientName.value = it }
        p.getProperty("session.language")?.let { name ->
            runCatching { AppLanguage.valueOf(name) }.getOrNull()?.let { session.appLanguage.value = it }
        }
        p.getProperty("session.darkTheme")?.toBooleanStrictOrNull()?.let { session.isDarkTheme.value = it }
        p.getProperty("session.privacyMode")?.toBooleanStrictOrNull()?.let { session.isPrivacyModeEnabled.value = it }

        p.getProperty("session.customCurrencies")?.let { encoded ->
            val decoded = decodeCurrencies(encoded)
            if (decoded.isNotEmpty()) {
                session.customCurrencies.value = decoded
                session.currencyRates.value = decoded
            }
        }
        p.getProperty("session.selectedCurrency")?.let { code ->
            session.selectedCurrency.value = code
            session.activeCurrency.value = CurrencyEngine.findByCode(code, session.customCurrencies.value)
        }
        p.getProperty("session.targetCurrency")?.let { session.targetCurrency.value = it }
        p.getProperty("session.fxCountryRegion")?.let { name ->
            runCatching { FxCountryRegion.valueOf(name) }.getOrNull()?.let { session.fxCountryRegion.value = it }
        }
        p.getProperty("session.isClientCountryEnabled")?.toBooleanStrictOrNull()?.let { session.isClientCountryEnabled.value = it }
        p.getProperty("session.fxRateMode")?.let { name ->
            runCatching { FxRateMode.valueOf(name) }.getOrNull()?.let { session.fxRateMode.value = it }
        }
        p.getProperty("session.fxSelectedPointId")?.let { session.fxSelectedPointId.value = it }
        p.getProperty("session.isFxAutoBestEnabled")?.toBooleanStrictOrNull()?.let { session.isFxAutoBestEnabled.value = it }
        p.getProperty("session.fxLastUpdatedAt")?.toLongOrNull()?.let { session.fxLastUpdatedAt.value = it }
        p.getProperty("session.fxAvailablePoints")?.let { encoded ->
            val points = decodeFxPoints(encoded)
            if (points.isNotEmpty()) {
                session.fxAvailablePoints.value = points
            }
        }

        // Financing & Multi-Currency Credit
        p.getProperty("fin.principal")?.let { financing.principalAmount.value = it }
        p.getProperty("fin.downPayment")?.let { financing.downPayment.value = it }
        p.getProperty("fin.rate")?.let { financing.annualInterestRate.value = it }
        p.getProperty("fin.tenure")?.let { financing.tenureMonths.value = it }
        p.getProperty("fin.frequency")?.let { name ->
            runCatching { PaymentFrequency.valueOf(name) }.getOrNull()?.let { financing.paymentFrequency.value = it }
        }
        p.getProperty("fin.graceEnabled")?.toBooleanStrictOrNull()?.let { financing.isGracePeriodEnabled.value = it }
        p.getProperty("fin.graceTenure")?.let { financing.graceTenureMonths.value = it }
        p.getProperty("fin.graceRate")?.let { financing.graceInterestRate.value = it }
        p.getProperty("fin.postGraceRate")?.let { financing.postGraceInterestRate.value = it }
        p.getProperty("fin.customMonthly")?.let { financing.customMonthlyPayment.value = it }
        p.getProperty("fin.scheme")?.let { name ->
            runCatching { AmortizationScheme.valueOf(name) }.getOrNull()?.let { financing.amortizationScheme.value = it }
        }

        p.getProperty("mc.principal")?.let { financing.creditPrincipal.value = it }
        p.getProperty("mc.tenure")?.let { financing.creditTenureMonths.value = it }
        p.getProperty("mc.currencies")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.let { list ->
            if (list.isNotEmpty()) financing.creditSelectedCurrencies.value = list
        }
        p.getProperty("mc.hedgeEnabled")?.toBooleanStrictOrNull()?.let { financing.creditHedgeEnabled.value = it }
        p.getProperty("mc.hedgeCost")?.let { financing.creditHedgeCostPct.value = it }
        p.getProperty("mc.fxShift")?.let { financing.creditExpectedFxShiftPct.value = it }
        p.getProperty("mc.rates")?.let { encoded ->
            val map = decodeDoubleMap(encoded)
            if (map.isNotEmpty()) financing.creditCurrencyRates.value = financing.creditCurrencyRates.value + map
        }
        p.getProperty("mc.allocations")?.let { encoded ->
            val map = decodeDoubleMap(encoded)
            if (map.isNotEmpty()) financing.creditAllocations.value = map
        }

        // Pricing, Landed Cost, Waterfall & Promo
        p.getProperty("land.category")?.let { name ->
            runCatching { CargoCategory.valueOf(name) }.getOrNull()?.let { pricing.selectedCategory.value = it }
        }
        p.getProperty("land.countryCode")?.let { code ->
            LandedCostCalculator.COUNTRY_PROFILES.firstOrNull { it.code == code }
                ?.let { pricing.selectedCountry.value = it }
        }
        p.getProperty("land.fob")?.let { pricing.fobPriceInput.value = it }
        p.getProperty("land.target")?.let { pricing.targetLandedCostInput.value = it }
        p.getProperty("land.coeff")?.let { pricing.expressCoeffInput.value = it }
        p.getProperty("land.reverse")?.toBooleanStrictOrNull()?.let { pricing.isLandedReverseMode.value = it }
        p.getProperty("land.logPct")?.let { pricing.customLogisticsPct.value = it }
        p.getProperty("land.dutyPct")?.let { pricing.customDutyPct.value = it }
        p.getProperty("land.vatPct")?.let { pricing.customVatPct.value = it }
        p.getProperty("land.brokerPct")?.let { pricing.customBrokerPct.value = it }

        p.getProperty("margin.cost")?.let { pricing.marginCostInput.value = it }
        p.getProperty("margin.price")?.let { pricing.marginPriceInput.value = it }

        p.getProperty("wf.rsp")?.let { pricing.waterfallRsp.value = it }
        p.getProperty("wf.vat")?.let { pricing.waterfallVat.value = it }
        p.getProperty("wf.retMargin")?.let { pricing.waterfallRetailerMargin.value = it }
        p.getProperty("wf.frontDisc")?.let { pricing.waterfallFrontDiscount.value = it }
        p.getProperty("wf.backBonus")?.let { pricing.waterfallBackBonus.value = it }
        p.getProperty("wf.logistics")?.let { pricing.waterfallLogistics.value = it }
        p.getProperty("wf.cogs")?.let { pricing.waterfallCogs.value = it }

        p.getProperty("promo.basePrice")?.let { pricing.promoBasePrice.value = it }
        p.getProperty("promo.unitCost")?.let { pricing.promoUnitCost.value = it }
        p.getProperty("promo.discPct")?.let { pricing.promoDiscountPct.value = it }
        p.getProperty("promo.baseVol")?.let { pricing.promoBaseVolume.value = it }
        p.getProperty("promo.simLift")?.let { pricing.promoSimulatedLift.value = it }

        // Working Capital, Funnel, P&L, Terms, Cash Gap, FX Stress & ROIC
        p.getProperty("ltv.arpu")?.let { workingCapital.arpu.value = it }
        p.getProperty("ltv.gm")?.let { workingCapital.grossMarginPct.value = it }
        p.getProperty("ltv.churn")?.let { workingCapital.churnRatePct.value = it }
        p.getProperty("ltv.cac")?.let { workingCapital.cac.value = it }

        p.getProperty("fn.leads")?.let { workingCapital.funnelLeads.value = it }
        p.getProperty("fn.mqls")?.let { workingCapital.funnelMqls.value = it }
        p.getProperty("fn.sqls")?.let { workingCapital.funnelSqls.value = it }
        p.getProperty("fn.opps")?.let { workingCapital.funnelOpps.value = it }
        p.getProperty("fn.won")?.let { workingCapital.funnelClosedWon.value = it }
        p.getProperty("fn.avgDeal")?.let { workingCapital.avgDealValue.value = it }

        p.getProperty("pnl.rev")?.let { workingCapital.pnlRevenue.value = it }
        p.getProperty("pnl.cogs")?.let { workingCapital.pnlCogs.value = it }
        p.getProperty("pnl.opex")?.let { workingCapital.pnlOpex.value = it }
        p.getProperty("pnl.inv")?.let { workingCapital.pnlInvestment.value = it }
        p.getProperty("pnl.dur")?.let { workingCapital.pnlDurationMonths.value = it }
        p.getProperty("pnl.fixed")?.let { workingCapital.fixedCosts.value = it }
        p.getProperty("pnl.unitPrice")?.let { workingCapital.unitPrice.value = it }
        p.getProperty("pnl.unitCost")?.let { workingCapital.unitCost.value = it }
        p.getProperty("pnl.tornadoDelta")?.toDoubleOrNull()?.let { workingCapital.tornadoDeltaPct.value = it }

        p.getProperty("terms.amt")?.let { workingCapital.termsDealAmount.value = it }
        p.getProperty("terms.days")?.let { workingCapital.termsDays.value = it }
        p.getProperty("terms.rate")?.let { workingCapital.termsAnnualRate.value = it }
        p.getProperty("terms.cashDisc")?.let { workingCapital.termsCashDiscount.value = it }

        p.getProperty("cg.rev")?.let { workingCapital.cashGapRevenue.value = it }
        p.getProperty("cg.cogs")?.let { workingCapital.cashGapCogs.value = it }
        p.getProperty("cg.prepaid")?.let { workingCapital.cashGapPrepaidPct.value = it }
        p.getProperty("cg.supDays")?.let { workingCapital.cashGapSupplierDays.value = it }
        p.getProperty("cg.custDays")?.let { workingCapital.cashGapCustomerDays.value = it }
        p.getProperty("cg.invDays")?.let { workingCapital.cashGapInventoryDays.value = it }
        p.getProperty("cg.overdraft")?.let { workingCapital.cashGapOverdraftRate.value = it }
        p.getProperty("cg.factAdv")?.let { workingCapital.cashGapFactoringAdvancePct.value = it }
        p.getProperty("cg.factFee")?.let { workingCapital.cashGapFactoringFeePct.value = it }
        p.getProperty("cg.factRate")?.let { workingCapital.cashGapFactoringRatePct.value = it }

        p.getProperty("fxs.price")?.let { workingCapital.fxContractPrice.value = it }
        p.getProperty("fxs.cogs")?.let { workingCapital.fxCogsForeign.value = it }
        p.getProperty("fxs.curr")?.let { workingCapital.fxForeignCurrency.value = it }
        p.getProperty("fxs.spot")?.let { workingCapital.fxSpotRate.value = it }
        p.getProperty("fxs.hedgedPct")?.let { workingCapital.fxHedgedPct.value = it }
        p.getProperty("fxs.hedgeRate")?.let { workingCapital.fxHedgeRate.value = it }
        p.getProperty("fxs.domCost")?.let { workingCapital.fxDomesticCost.value = it }

        p.getProperty("roic.rev")?.let { workingCapital.hurdleDealRevenue.value = it }
        p.getProperty("roic.profit")?.let { workingCapital.hurdleNetProfit.value = it }
        p.getProperty("roic.cap")?.let { workingCapital.hurdleTiedUpCapital.value = it }
        p.getProperty("roic.days")?.let { workingCapital.hurdleCycleDays.value = it }
        p.getProperty("roic.rate")?.let { workingCapital.hurdleCorporateRate.value = it }

        // KAM, Plan-Fact, Analyst & Deal Approval
        if (kamVault != null) {
            p.getProperty("pf.pPrice")?.let { kamVault.planFactPlanPrice.value = it }
            p.getProperty("pf.pVol")?.let { kamVault.planFactPlanVolume.value = it }
            p.getProperty("pf.pCost")?.let { kamVault.planFactPlanCost.value = it }
            p.getProperty("pf.pDays")?.let { kamVault.planFactPlanDays.value = it }
            p.getProperty("pf.aPrice")?.let { kamVault.planFactActualPrice.value = it }
            p.getProperty("pf.aVol")?.let { kamVault.planFactActualVolume.value = it }
            p.getProperty("pf.aCost")?.let { kamVault.planFactActualCost.value = it }
            p.getProperty("pf.aDays")?.let { kamVault.planFactActualDays.value = it }
            p.getProperty("pf.aPen")?.let { kamVault.planFactActualPenalties.value = it }
            p.getProperty("pf.capRate")?.let { kamVault.planFactCapitalRate.value = it }

            p.getProperty("kam.shelf")?.let { kamVault.kamBaseShelfPrice.value = it }
            p.getProperty("kam.cogs")?.let { kamVault.kamBaseCogs.value = it }
            p.getProperty("kam.scenario")?.let { name ->
                runCatching { KamScenarioType.valueOf(name) }.getOrNull()?.let { kamVault.kamActiveScenarioType.value = it }
            }
            p.getProperty("kam.dealAmt")?.let { kamVault.kamDealAmount.value = it }
            p.getProperty("kam.fill")?.let { kamVault.kamFillRatePct.value = it }
            p.getProperty("kam.pen")?.let { kamVault.kamPenaltyRatePct.value = it }
            p.getProperty("kam.slotPen")?.let { kamVault.kamTimeSlotPenalty.value = it }

            p.getProperty("gg.type")?.let { name ->
                runCatching { GiveConcessionType.valueOf(name) }.getOrNull()?.let { kamVault.giveConcessionType.value = it }
            }
            p.getProperty("gg.val")?.let { kamVault.giveConcessionValue.value = it }

            p.getProperty("to.amt")?.let { kamVault.tradeOffDealAmount.value = it }
            p.getProperty("to.disc")?.let { kamVault.tradeOffDiscountPct.value = it }
            p.getProperty("to.days")?.let { kamVault.tradeOffDeferralDays.value = it }
            p.getProperty("to.rate")?.let { kamVault.tradeOffAnnualRate.value = it }

            p.getProperty("ca.basket")?.let { encoded ->
                val basket = decodeBasket(encoded)
                if (basket.isNotEmpty()) kamVault.restoreBasketItems(basket)
            }
            p.getProperty("ca.rebVol")?.let { kamVault.rebateActualVolume.value = it }
            p.getProperty("ca.rebMargin")?.let { kamVault.rebateBaseMarginPct.value = it }

            p.getProperty("deal.clientId")?.let { kamVault.selectedClientId.value = it }
            p.getProperty("deal.sp")?.let { kamVault.dealApprovalSellingPrice.value = it }
            p.getProperty("deal.cp")?.let { kamVault.dealApprovalCostPrice.value = it }
            p.getProperty("deal.vol")?.let { kamVault.dealApprovalVolumeUnits.value = it }
            p.getProperty("deal.disc")?.let { kamVault.dealApprovalDiscountPct.value = it }
            p.getProperty("deal.bonus")?.let { kamVault.dealApprovalBonusPct.value = it }
            p.getProperty("deal.days")?.let { kamVault.dealApprovalPaymentDays.value = it }
            p.getProperty("deal.rate")?.let { kamVault.dealApprovalFinancingRate.value = it }
            p.getProperty("deal.status")?.let { name ->
                runCatching { DealApprovalStatus.valueOf(name) }.getOrNull()?.let { kamVault.dealApprovalStatus.value = it }
            }
        }

        recalculateAll()
        return true
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun dec(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun encodeDoubleMap(map: Map<String, Double>): String =
        map.entries.joinToString(";") { "${enc(it.key)}:${it.value}" }

    private fun decodeDoubleMap(raw: String): Map<String, Double> {
        if (raw.isBlank()) return emptyMap()
        val result = linkedMapOf<String, Double>()
        raw.split(";").forEach { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size == 2) {
                val v = parts[1].toDoubleOrNull()
                if (v != null && v.isFinite()) result[dec(parts[0])] = v
            }
        }
        return result
    }

    private fun encodeCurrencies(list: List<CurrencyRate>): String =
        list.joinToString("\n") { "${enc(it.code)}|${enc(it.name)}|${enc(it.symbol)}|${it.rateToBaseUsd}" }

    private fun decodeCurrencies(raw: String): List<CurrencyRate> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size == 4) {
                val rate = parts[3].toDoubleOrNull()
                if (rate != null && rate.isFinite() && rate > 0.0) {
                    CurrencyRate(dec(parts[0]), dec(parts[1]), dec(parts[2]), rate)
                } else null
            } else null
        }.toList()
    }

    private fun encodeBasket(items: List<BasketSkuItem>): String =
        items.joinToString("\n") { "${enc(it.id)}|${enc(it.name)}|${it.volume}|${it.unitPrice}|${it.unitCogs}" }

    private fun decodeBasket(raw: String): List<BasketSkuItem> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size == 5) {
                val vol = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val price = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                val cogs = parts[4].toDoubleOrNull() ?: return@mapNotNull null
                BasketSkuItem(dec(parts[0]), dec(parts[1]), vol, price, cogs)
            } else null
        }.toList()
    }

    private fun encodeFxPoints(points: List<FxRateSourcePoint>): String =
        points.joinToString("\n") { pt ->
            listOf(
                enc(pt.id),
                pt.country.name,
                enc(pt.providerNameEn),
                enc(pt.providerNameRu),
                enc(pt.pointDetailsEn),
                enc(pt.pointDetailsRu),
                encodeDoubleMap(pt.ratesToUsd),
                pt.fetchedAtMillis.toString(),
                pt.isCentralBank.toString()
            ).joinToString("|")
        }

    private fun decodeFxPoints(raw: String): List<FxRateSourcePoint> {
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size == 9) {
                val country = runCatching { FxCountryRegion.valueOf(parts[1]) }.getOrNull() ?: return@mapNotNull null
                val rates = decodeDoubleMap(parts[6])
                val fetched = parts[7].toLongOrNull() ?: return@mapNotNull null
                val isCb = parts[8].toBooleanStrictOrNull() ?: false
                FxRateSourcePoint(
                    id = dec(parts[0]),
                    country = country,
                    providerNameEn = dec(parts[2]),
                    providerNameRu = dec(parts[3]),
                    pointDetailsEn = dec(parts[4]),
                    pointDetailsRu = dec(parts[5]),
                    ratesToUsd = rates,
                    fetchedAtMillis = fetched,
                    isCentralBank = isCb
                )
            } else null
        }.toList()
    }
}
