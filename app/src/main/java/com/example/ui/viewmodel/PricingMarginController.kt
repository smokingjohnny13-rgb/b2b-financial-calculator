package com.example.ui.viewmodel

import com.example.data.model.CargoCategory
import com.example.data.model.CountryTradeProfile
import com.example.data.model.LandedCostResult
import com.example.data.model.MarginMarkupState
import com.example.domain.calculator.LandedCostCalculator
import com.example.domain.calculator.MarginMarkupSolver
import com.example.domain.calculator.PriceWaterfallEngine
import com.example.domain.calculator.PriceWaterfallResult
import com.example.domain.calculator.PromoBreakEvenEngine
import com.example.domain.calculator.PromoBreakEvenResult
import com.example.util.toCleanDouble
import com.example.util.toCleanInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Controller for FOB/Landed Cost, Margin/Markup Solver, Retail Price Waterfall,
 * and Promo Break-Even modeling.
 */
class PricingMarginController(
    private val session: DealSessionState
) {
    // Landed Cost Matrix
    val selectedCategory = MutableStateFlow(CargoCategory.INDUSTRIAL)
    val selectedCountry = MutableStateFlow(LandedCostCalculator.COUNTRY_PROFILES[0])
    val fobPriceInput = MutableStateFlow("10000")
    val targetLandedCostInput = MutableStateFlow("14800")
    val expressCoeffInput = MutableStateFlow("1.48")
    val isLandedReverseMode = MutableStateFlow(false)

    val customLogisticsPct = MutableStateFlow("15.0")
    val customDutyPct = MutableStateFlow("7.5")
    val customVatPct = MutableStateFlow("20.0")
    val customBrokerPct = MutableStateFlow("2.5")

    private val _landedCostResult = MutableStateFlow(
        LandedCostCalculator.calculateDirect(
            fobPrice = 10000.0,
            coefficient = 1.48,
            logisticsPct = 15.0,
            dutyPct = 7.5,
            vatPct = 20.0,
            brokerFeesPct = 2.5
        )
    )
    val landedCostResult: StateFlow<LandedCostResult> = _landedCostResult.asStateFlow()

    // Margin & Markup
    val marginCostInput = MutableStateFlow("1000")
    val marginPriceInput = MutableStateFlow("1600")
    private val _marginState = MutableStateFlow(
        MarginMarkupSolver.fromCostAndPrice(1000.0, 1600.0)
    )
    val marginState: StateFlow<MarginMarkupState> = _marginState.asStateFlow()

    // Price Waterfall
    val waterfallRsp = MutableStateFlow("100")
    val waterfallVat = MutableStateFlow("20")
    val waterfallRetailerMargin = MutableStateFlow("30")
    val waterfallFrontDiscount = MutableStateFlow("5")
    val waterfallBackBonus = MutableStateFlow("8")
    val waterfallLogistics = MutableStateFlow("3.5")
    val waterfallCogs = MutableStateFlow("25")

    private val _waterfallResult = MutableStateFlow(
        PriceWaterfallEngine.calculate(
            shelfRspWithVat = 100.0,
            vatRatePct = 20.0,
            retailerMarginPct = 30.0,
            frontDiscountPct = 5.0,
            backBonusPct = 8.0,
            logisticsCostPerUnit = 3.5,
            supplierCogs = 25.0
        )
    )
    val waterfallResult: StateFlow<PriceWaterfallResult> = _waterfallResult.asStateFlow()

    // Promo Break-Even
    val promoBasePrice = MutableStateFlow("50")
    val promoUnitCost = MutableStateFlow("30")
    val promoDiscountPct = MutableStateFlow("20")
    val promoBaseVolume = MutableStateFlow("1000")
    val promoSimulatedLift = MutableStateFlow("50")

    private val _promoResult = MutableStateFlow(
        PromoBreakEvenEngine.calculate(
            regularPrice = 50.0,
            unitCost = 30.0,
            promoDiscountPct = 20.0,
            baseVolume = 1000,
            simulatedVolumeLiftPct = 50.0
        )
    )
    val promoResult: StateFlow<PromoBreakEvenResult> = _promoResult.asStateFlow()

    fun updateLandedCostCalculations() {
        val fob = fobPriceInput.value.toCleanDouble() ?: 0.0
        val targetLanded = targetLandedCostInput.value.toCleanDouble() ?: 0.0
        val coeff = expressCoeffInput.value.toCleanDouble() ?: 1.48

        val logPct = customLogisticsPct.value.toCleanDouble() ?: 15.0
        val dutyPct = customDutyPct.value.toCleanDouble() ?: 7.5
        val vatPct = customVatPct.value.toCleanDouble() ?: 20.0
        val brokerPct = customBrokerPct.value.toCleanDouble() ?: 2.5

        if (isLandedReverseMode.value) {
            val result = LandedCostCalculator.calculateReverse(
                targetLandedCost = targetLanded,
                coefficient = coeff,
                logisticsPct = logPct,
                dutyPct = dutyPct,
                vatPct = vatPct,
                brokerFeesPct = brokerPct
            )
            _landedCostResult.value = result
            fobPriceInput.value = result.fobPrice.toInt().toString()
        } else {
            val result = LandedCostCalculator.calculateDirect(
                fobPrice = fob,
                coefficient = coeff,
                logisticsPct = logPct,
                dutyPct = dutyPct,
                vatPct = vatPct,
                brokerFeesPct = brokerPct
            )
            _landedCostResult.value = result
            targetLandedCostInput.value = result.landedCost.toInt().toString()
        }
    }

    fun setLandedReverseMode(reverse: Boolean) {
        isLandedReverseMode.value = reverse
        updateLandedCostCalculations()
    }

    fun applyCargoCategory(category: CargoCategory) {
        selectedCategory.value = category
        if (category != CargoCategory.CUSTOM) {
            expressCoeffInput.value = category.defaultCoeff.toString()
            customLogisticsPct.value = category.avgLogisticsPct.toString()
            customDutyPct.value = category.avgDutyPct.toString()
        }
        updateLandedCostCalculations()
    }

    fun applyCountryTradeProfile(country: CountryTradeProfile) {
        selectedCountry.value = country
        customVatPct.value = country.defaultVatPct.toString()
        customDutyPct.value = country.defaultDutyPct.toString()
        customLogisticsPct.value = country.defaultLogisticsPct.toString()
        customBrokerPct.value = country.defaultBrokerFeesPct.toString()
        recalculateCustomCoefficient()
    }

    fun recalculateCustomCoefficient() {
        val logPct = customLogisticsPct.value.toCleanDouble() ?: 15.0
        val dutyPct = customDutyPct.value.toCleanDouble() ?: 7.5
        val vatPct = customVatPct.value.toCleanDouble() ?: 20.0
        val brokerPct = customBrokerPct.value.toCleanDouble() ?: 2.5
        val computedCoeff = LandedCostCalculator.buildCoefficientFromComponents(
            logisticsPct = logPct,
            dutyPct = dutyPct,
            vatPct = vatPct,
            brokerFeesPct = brokerPct
        )
        expressCoeffInput.value = computedCoeff.toString()
        updateLandedCostCalculations()
    }

    fun calibrateCoefficientFromPrices() {
        val fob = fobPriceInput.value.toCleanDouble() ?: 0.0
        val landed = targetLandedCostInput.value.toCleanDouble() ?: 0.0
        val calibrated = LandedCostCalculator.calibrateCoefficient(fob, landed)
        expressCoeffInput.value = calibrated.toString()
        selectedCategory.value = CargoCategory.CUSTOM
        updateLandedCostCalculations()
        session.emitLocalized(
            "Calibrated Express Multiplier: $calibrated",
            "Вычислен экспресс-коэффициент: $calibrated"
        )
    }

    fun transferLandedCostToMarginSolver() {
        applyFobLandedCostToMargin(_landedCostResult.value.landedCost)
    }

    fun applyFobLandedCostToMargin(landedCost: Double) {
        marginCostInput.value = landedCost.toInt().toString()
        val currentPrice = marginPriceInput.value.toCleanDouble() ?: 0.0
        if (currentPrice <= landedCost) {
            marginPriceInput.value = (landedCost / (1.0 - 0.35)).toInt().toString()
        }
        onMarginCostOrPriceChanged()
        session.emitLocalized(
            "Landed cost ${session.formatCurrency(landedCost)} applied from FOB",
            "Себестоимость ${session.formatCurrency(landedCost)} применена из FOB"
        )
    }

    fun onMarginCostOrPriceChanged(newCost: String? = null, newPrice: String? = null) {
        newCost?.let { marginCostInput.value = it }
        newPrice?.let { marginPriceInput.value = it }
        val cost = marginCostInput.value.toCleanDouble() ?: 0.0
        val price = marginPriceInput.value.toCleanDouble() ?: 0.0
        _marginState.value = MarginMarkupSolver.fromCostAndPrice(cost, price)
    }

    fun applyMarginPercentPreset(marginPct: Double) {
        val cost = marginCostInput.value.toCleanDouble() ?: 0.0
        _marginState.value = MarginMarkupSolver.fromCostAndMargin(cost, marginPct)
        val sp = _marginState.value.sellingPrice
        marginPriceInput.value = session.formatCleanNum(sp)
        session.emitLocalized(
            "Target margin ${marginPct.toInt()}% applied (Selling Price: ${session.formatCurrency(sp)})",
            "Установлена целевая маржа ${marginPct.toInt()}% (Цена продажи: ${session.formatCurrency(sp)})"
        )
    }

    fun applyMarkupPercentPreset(markupPct: Double) {
        val cost = marginCostInput.value.toCleanDouble() ?: 0.0
        _marginState.value = MarginMarkupSolver.fromCostAndMarkup(cost, markupPct)
        val sp = _marginState.value.sellingPrice
        marginPriceInput.value = session.formatCleanNum(sp)
        session.emitLocalized(
            "Target markup ${markupPct.toInt()}% applied (Selling Price: ${session.formatCurrency(sp)})",
            "Установлена целевая наценка ${markupPct.toInt()}% (Цена продажи: ${session.formatCurrency(sp)})"
        )
    }

    fun updateWaterfallCalculations() {
        val rsp = waterfallRsp.value.toCleanDouble() ?: 100.0
        val vat = waterfallVat.value.toCleanDouble() ?: 20.0
        val retMargin = waterfallRetailerMargin.value.toCleanDouble() ?: 30.0
        val frontDisc = waterfallFrontDiscount.value.toCleanDouble() ?: 5.0
        val backBonus = waterfallBackBonus.value.toCleanDouble() ?: 8.0
        val logistics = waterfallLogistics.value.toCleanDouble() ?: 3.5
        val cogs = waterfallCogs.value.toCleanDouble() ?: 25.0

        _waterfallResult.value = PriceWaterfallEngine.calculate(
            shelfRspWithVat = rsp,
            vatRatePct = vat,
            retailerMarginPct = retMargin,
            frontDiscountPct = frontDisc,
            backBonusPct = backBonus,
            logisticsCostPerUnit = logistics,
            supplierCogs = cogs
        )
    }

    fun importCogsFromLandedCost() {
        val landed = _landedCostResult.value.landedCost
        val unitCogs = if (landed > 500.0 && (waterfallRsp.value.toCleanDouble() ?: 100.0) < 500.0) {
            landed / 1000.0
        } else {
            landed
        }
        waterfallCogs.value = if (unitCogs % 1.0 == 0.0) unitCogs.toInt().toString() else String.format(Locale.US, "%.1f", unitCogs)
        updateWaterfallCalculations()
        session.emitLocalized(
            "COGS updated from Landed Cost: ${session.formatCurrency(unitCogs)}",
            "Себестоимость COGS обновлена из Landed Cost: ${session.formatCurrency(unitCogs)}"
        )
    }

    fun updatePromoCalculations() {
        val price = promoBasePrice.value.toCleanDouble() ?: 50.0
        val cost = promoUnitCost.value.toCleanDouble() ?: 30.0
        val disc = promoDiscountPct.value.toCleanDouble() ?: 20.0
        val vol = promoBaseVolume.value.toCleanInt() ?: 1000
        val simLift = promoSimulatedLift.value.toCleanDouble() ?: 50.0

        _promoResult.value = PromoBreakEvenEngine.calculate(
            regularPrice = price,
            unitCost = cost,
            promoDiscountPct = disc,
            baseVolume = vol,
            simulatedVolumeLiftPct = simLift
        )
    }

    fun importPromoFromWaterfall() {
        val net = _waterfallResult.value.netNetPrice
        val cogs = _waterfallResult.value.supplierCogs
        promoBasePrice.value = net.toString()
        promoUnitCost.value = cogs.toString()
        updatePromoCalculations()
        session.emitLocalized(
            "Promo data loaded from Price Waterfall (Price: ${session.formatCurrency(net)}, Cost: ${session.formatCurrency(cogs)})",
            "Данные для промо загружены из Водопада цен (Цена: ${session.formatCurrency(net)}, Себестоимость: ${session.formatCurrency(cogs)})"
        )
    }
}
