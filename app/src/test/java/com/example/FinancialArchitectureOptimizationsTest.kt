package com.example

import com.example.data.model.PaymentFrequency
import com.example.domain.calculator.AmortizationEngine
import com.example.domain.calculator.CurrencyCreditLegInput
import com.example.domain.calculator.MultiCurrencyCreditEngine
import com.example.util.toCleanDouble
import com.example.util.toCleanInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialArchitectureOptimizationsTest {

    @Test
    fun `test amortization schedule generates full 240 periods without 120-cutoff`() {
        // 20-year monthly loan = 240 periods
        val summary = AmortizationEngine.calculate(
            principal = 500000.0,
            annualInterestRatePct = 7.5,
            tenureMonths = 240,
            downPayment = 50000.0,
            frequency = PaymentFrequency.MONTHLY
        )

        assertEquals(240, summary.schedule.size)
        assertEquals(1, summary.schedule.first().period)
        assertEquals(240, summary.schedule.last().period)
        assertEquals(0.0, summary.schedule.last().remainingBalance, 0.05)
        assertTrue(summary.totalInterest > 0.0)
    }

    @Test
    fun `test floating rate amortization updates rates and recalculates installments`() {
        // 12-month loan, quarterly reset (every 3 months)
        // Starting benchmark 16% + spread 2% = 18%, trend +2% per year
        val summary = AmortizationEngine.calculate(
            principal = 120000.0,
            annualInterestRatePct = 18.0,
            tenureMonths = 12,
            frequency = PaymentFrequency.MONTHLY,
            isFloatingRate = true,
            floatingBenchmarkRatePct = 16.0,
            floatingSpreadPct = 2.0,
            floatingResetIntervalMonths = 3,
            floatingRateTrendAnnualPct = 2.0
        )

        assertTrue(summary.isFloatingRate)
        assertEquals(12, summary.schedule.size)

        // Period 1 to 3 should be at 18.0%
        assertEquals(18.0, summary.schedule[0].rateAppliedPct, 0.01)
        assertEquals(18.0, summary.schedule[2].rateAppliedPct, 0.01)

        // Period 4 to 6: rate increases by 2.0% * (3/12) = +0.5% => 18.5%
        assertEquals(18.5, summary.schedule[3].rateAppliedPct, 0.01)

        // Final balance should reach 0.0
        assertEquals(0.0, summary.schedule.last().remainingBalance, 0.05)
    }

    @Test
    fun `test multi-currency credit scales FX shift and compounds over multi-year horizon`() {
        val legs = listOf(
            CurrencyCreditLegInput(
                currencyCode = "EUR",
                annualRatePct = 5.0,
                allocationPct = 100.0,
                hedgeCostAnnualPct = 2.0,
                expectedFxShiftPct = 10.0, // 10% per annum
                isHedged = false
            )
        )

        // 3-year facility (36 months): cumulative FX shift should compound: (1 + 0.10)^3 - 1 = 33.1%
        val result3Years = MultiCurrencyCreditEngine.calculate(
            dealAmount = 100000.0,
            baseCurrencyCode = "USD",
            tenureMonths = 36,
            legsInput = legs
        )

        val leg = result3Years.legs.first()
        // Spot cross rate USD to EUR is ~0.92, so 100k USD = 92k EUR
        // Principal in currency = 92,000 EUR
        // 3-year compound interest: 92,000 * ((1 + 0.05)^3 - 1) ~= 14,500.50 EUR
        // Total in EUR ~= 106,500.50 EUR
        assertTrue(leg.totalRepaymentInCurrency > 105000.0)

        // Unhedged repayment in base should reflect the compounded 33.1% FX depreciation
        assertTrue(leg.totalRepaymentInBaseUnhedged > 150000.0)
    }

    @Test
    fun `test toCleanDouble and toCleanInt handles localized comma and whitespace safely`() {
        assertEquals(12.5, "12,5".toCleanDouble() ?: 0.0, 0.001)
        assertEquals(1000.75, " 1000,75 ".toCleanDouble() ?: 0.0, 0.001)
        assertEquals(0.0, "".toCleanDouble(0.0), 0.001)
        assertEquals(55.0, "abc".toCleanDouble(55.0), 0.001)

        assertEquals(24, "24".toCleanInt() ?: 0)
        assertEquals(36, "36,0".toCleanInt() ?: 0)
        assertEquals(10, " 10 ".toCleanInt() ?: 0)
        assertEquals(12, "".toCleanInt(12))

        assertNull("NaN".toCleanDouble())
        assertNull("Infinity".toCleanDouble())
        assertNull("-Infinity".toCleanDouble())
        assertEquals(0.0, "NaN".toCleanDouble(0.0), 0.001)
        assertNull("NaN".toCleanInt())
        assertNull("Infinity".toCleanInt())
    }

    @Test
    fun `test cross module deal cash gap and hurdle consistency`() {
        // Given a deal of 1,000 units with Net-Net price = 100 and COGS = 65 (Margin = 35%)
        val volume = 1000
        val sellingPrice = 100.0
        val costPrice = 65.0
        val paymentDays = 45

        val dealRevenue = sellingPrice * volume // 100,000
        val dealCogs = costPrice * volume       // 65,000

        // 1. Deal Scoring
        val dealScoring = com.example.domain.calculator.DealScoringEngine.calculate(
            sellingPrice = sellingPrice,
            costPrice = costPrice,
            volumeUnits = volume,
            onInvoiceDiscountPct = 0.0,
            offInvoiceBonusPct = 0.0,
            paymentTermsDays = paymentDays,
            financingAnnualRatePct = 18.0
        )
        assertEquals(dealRevenue, dealScoring.totalRevenue, 0.01)
        assertEquals(dealCogs, dealScoring.costPrice * dealScoring.volumeUnits, 0.01)
        assertEquals(35000.0, dealScoring.totalNetNetProfit, 0.01)

        // 2. Cash Gap calculation with the exact transferred deal parameters
        val cashGap = com.example.domain.calculator.CashGapEngine.calculate(
            dealRevenue = dealRevenue,
            cogsTotal = dealCogs,
            prepaidPct = 10.0,
            supplierPayDays = 30,
            customerPayDays = paymentDays,
            inventoryDays = 15,
            overdraftRatePct = 18.0
        )
        // CCC = DIO (15) + DSO (45) - DPO (30) = 30 days
        assertEquals(30, cashGap.cashConversionCycleDays)
        assertTrue("Cash gap peak cash deficit must be positive", cashGap.peakCashDeficit > 0.0)

        // 3. Hurdle ROIC calculation with the exact transferred tied-up capital
        val roic = com.example.domain.calculator.HurdleRoicEngine.calculate(
            dealRevenue = dealRevenue,
            dealNetProfit = dealScoring.totalNetNetProfit,
            tiedUpCapital = dealCogs,
            cycleDays = paymentDays,
            corporateHurdleRatePct = 20.0
        )
        // Annualized ROIC: (35,000 / 65,000) * (365 / 45) * 100% = 53.85% * 8.11 = ~436.7%
        assertTrue("ROIC must exceed corporate hurdle of 20%", roic.annualizedRoicPct > 20.0)
        assertTrue("Deal must be value accretive", roic.isValueAccretive)
    }

    @Test
    fun `test plan fact variance bridge mathematical decomposition and identity`() {
        val result = com.example.domain.calculator.PlanFactVarianceEngine.calculate(
            planPrice = 100.0,
            planVolume = 1000,
            planCost = 60.0,
            planDays = 30,
            actualPrice = 95.0,
            actualVolume = 1100,
            actualCost = 64.0,
            actualDays = 50,
            actualPenalties = 1500.0,
            capitalRatePct = 18.0
        )

        // 1. Plan and Actual Totals
        assertEquals(100000.0, result.planRevenue, 0.01)
        assertEquals(60000.0, result.planCogs, 0.01)
        assertEquals(40000.0, result.planGrossProfit, 0.01)
        assertEquals(40.0, result.planMarginPct, 0.01)

        assertEquals(104500.0, result.actualRevenue, 0.01) // 95 * 1100
        assertEquals(70400.0, result.actualCogs, 0.01)     // 64 * 1100
        assertEquals(34100.0, result.actualGrossProfit, 0.01) // 31 * 1100

        // 2. Factorial Bridge Verification
        // Volume Effect = (1100 - 1000) * (100 - 60) = +4,000.0
        assertEquals(4000.0, result.volumeEffectProfit, 0.01)

        // Price Effect = (95 - 100) * 1100 = -5,500.0
        assertEquals(-5500.0, result.priceEffectProfit, 0.01)

        // Cost Effect = (60 - 64) * 1100 = -4,400.0
        assertEquals(-4400.0, result.costEffectProfit, 0.01)

        // Penalties Effect = -1,500.0
        assertEquals(-1500.0, result.penaltiesEffectProfit, 0.01)

        // Deferral variance: -(Actual Deferral - Plan Deferral)
        // Plan Deferral = 100k * 0.18 * 30/365 = 1,479.45
        // Actual Deferral = 104.5k * 0.18 * 50/365 = 2,576.71
        // Deferral Effect = -(2576.71 - 1479.45) = -1,097.26
        assertEquals(-1097.26, result.dsoDeferralEffectProfit, 0.05)

        // Mathematical Bridge Sum Identity:
        // Sum of all 5 factors MUST equal total Net Profit Delta (up to rounding)
        val bridgeSum = result.volumeEffectProfit +
                result.priceEffectProfit +
                result.costEffectProfit +
                result.dsoDeferralEffectProfit +
                result.penaltiesEffectProfit

        assertEquals(result.netProfitDelta, bridgeSum, 0.1)

        // Status must identify erosion due to multiple negative factors
        assertEquals(com.example.domain.calculator.PlanFactHealthStatus.EROSION, result.status)
        assertTrue(result.verdictRu.contains("Эрозия прибыли"))
    }

    @Test
    fun `test early repayment in standard credit reduces tenure and total interest`() {
        val standard = AmortizationEngine.calculate(
            principal = 100000.0,
            annualInterestRatePct = 12.0,
            tenureMonths = 36,
            frequency = PaymentFrequency.MONTHLY,
            customPeriodicPayment = null
        )
        assertEquals(36, standard.schedule.size)
        assertTrue("Standard total interest > 15,000", standard.totalInterest > 15000.0)

        // Accelerate with 5,000 monthly payment
        val accelerated = AmortizationEngine.calculate(
            principal = 100000.0,
            annualInterestRatePct = 12.0,
            tenureMonths = 36,
            frequency = PaymentFrequency.MONTHLY,
            customPeriodicPayment = 5000.0
        )
        assertTrue(accelerated.isAccelerated)
        assertTrue("Accelerated loan finishes earlier than 36 months", accelerated.schedule.size < 36)
        assertTrue("Saved at least 10 periods", accelerated.periodsSaved >= 10)
        assertTrue("Saved substantial interest", accelerated.interestSaved > 5000.0)
        assertEquals(0.0, accelerated.schedule.last().remainingBalance, 0.05)
    }

    @Test
    fun `test grace period with custom payment reduces principal during grace`() {
        // 100k, 12 months grace at 0.01%, 36 months total, 10% post-grace
        val withGraceCustom = AmortizationEngine.calculate(
            principal = 100000.0,
            annualInterestRatePct = 10.0,
            tenureMonths = 36,
            hasGracePeriod = true,
            graceTenureMonths = 12,
            graceInterestRatePct = 0.01,
            postGraceInterestRatePct = 10.0,
            frequency = PaymentFrequency.MONTHLY,
            customPeriodicPayment = 3000.0
        )

        assertTrue(withGraceCustom.hasGracePeriod)
        // In month 12 (end of grace), balance should be significantly lower than 100,000
        val balanceEndGrace = withGraceCustom.schedule[11].remainingBalance
        assertTrue("Balance after grace must be reduced by custom payments", balanceEndGrace < 70000.0)
        assertTrue("Saved interest vs baseline", withGraceCustom.interestSaved > 0.0)
    }

    @Test
    fun `test landed cost to deal synchronization math`() {
        // FOB 10.0, express coeff 1.35
        val landedResult = com.example.domain.calculator.LandedCostCalculator.calculateDirect(
            fobPrice = 10.0,
            coefficient = 1.35,
            logisticsPct = 15.0,
            dutyPct = 7.5,
            vatPct = 20.0,
            brokerFeesPct = 2.5
        )
        assertTrue("Landed cost per unit must be greater than FOB", landedResult.landedCost > 10.0)

        // Deal Scoring verification with landed cost
        val scoring = com.example.domain.calculator.DealScoringEngine.calculate(
            sellingPrice = landedResult.landedCost / (1.0 - 0.35), // 35% margin
            costPrice = landedResult.landedCost,
            volumeUnits = 1000,
            onInvoiceDiscountPct = 5.0,
            offInvoiceBonusPct = 2.0,
            paymentTermsDays = 30,
            financingAnnualRatePct = 14.0
        )
        assertTrue("Net-Net Margin must remain healthy", scoring.netNetMarginPct > 20.0)
        assertTrue("Deal revenue equals volume * net price", scoring.totalRevenue > 0.0)
    }

    @Test
    fun `test give get discount post concession margin and negative margin guard`() {
        // $100 selling price, $60 COGS, 0% prior discount/bonus -> 40% margin.
        // Giving +10% discount => $90 Net-Net price, $30 profit => exact margin 30/90 = 33.33%
        val discountRes = com.example.domain.calculator.GiveGetEngine.calculate(
            sellingPrice = 100.0,
            cogs = 60.0,
            baseVolume = 1000,
            currentDiscountPct = 0.0,
            currentBonusPct = 0.0,
            concessionType = com.example.domain.calculator.GiveConcessionType.PRICE_DISCOUNT,
            concessionValue = 10.0
        )
        assertEquals(10000.0, discountRes.concessionFinancialCost, 0.01)
        assertEquals(33.33, discountRes.postConcessionMarginPct, 0.05)
        assertEquals(6.67, discountRes.marginErosionPp, 0.05)

        // Loss-making deal ($50 price < $60 COGS) must not fabricate positive contribution or volume lift
        val lossRes = com.example.domain.calculator.GiveGetEngine.calculate(
            sellingPrice = 50.0,
            cogs = 60.0,
            baseVolume = 1000,
            currentDiscountPct = 0.0,
            currentBonusPct = 0.0,
            concessionType = com.example.domain.calculator.GiveConcessionType.LISTING_FEE,
            concessionValue = 1000.0
        )
        assertEquals(-10.0, lossRes.unitContributionMargin, 0.01)
        assertEquals(0, lossRes.requiredVolumeLiftUnits)
        assertTrue(lossRes.counterTrades.first { it.id == "volume_lift" }.valueHeadlineEn.contains("Infeasible"))
        assertFalse(lossRes.negotiationScriptEn.contains("+0.0%"))
        assertFalse(lossRes.negotiationScriptRu.contains("+0.0%"))
    }

    @Test
    fun `test floating rate prepayment calculates exact interestSaved`() {
        val accelerated = AmortizationEngine.calculate(
            principal = 120000.0,
            annualInterestRatePct = 18.0,
            tenureMonths = 24,
            frequency = PaymentFrequency.MONTHLY,
            isFloatingRate = true,
            floatingBenchmarkRatePct = 16.0,
            floatingSpreadPct = 2.0,
            floatingResetIntervalMonths = 6,
            floatingRateTrendAnnualPct = 0.0,
            customPeriodicPayment = 8000.0
        )
        assertTrue("Floating prepayment should accelerate payoff", accelerated.isAccelerated)
        assertTrue("Floating prepayment should save periods", accelerated.periodsSaved > 0)
        assertTrue("Floating prepayment should report positive interestSaved", accelerated.interestSaved > 1000.0)
        assertEquals(0.0, accelerated.schedule.last().remainingBalance, 0.05)
    }

    @Test
    fun `test landed cost compound coefficient matches itemized breakdown`() {
        val coeff = com.example.domain.calculator.LandedCostCalculator.buildCoefficientFromComponents(
            logisticsPct = 15.0,
            dutyPct = 10.0,
            vatPct = 20.0,
            brokerFeesPct = 2.5
        )
        // (1.15 * 1.10 * 1.20) + 0.025 = 1.518 + 0.025 = 1.5430
        assertEquals(1.5430, coeff, 0.0001)

        val direct = com.example.domain.calculator.LandedCostCalculator.calculateDirect(
            fobPrice = 100.0,
            coefficient = coeff,
            logisticsPct = 15.0,
            dutyPct = 10.0,
            vatPct = 20.0,
            brokerFeesPct = 2.5
        )
        val componentsSum = direct.fobPrice + direct.freightAmount + direct.customsDutyAmount + direct.importVatAmount + direct.brokerFeesAmount
        assertEquals(direct.landedCost, componentsSum, 0.05)
    }

    @Test
    fun `test multi currency credit preserves precision on low-unit base and syncs currency switch`() {
        val kztToUsd = MultiCurrencyCreditEngine.calculate(
            dealAmount = 100000.0,
            baseCurrencyCode = "KZT",
            tenureMonths = 12,
            legsInput = listOf(
                CurrencyCreditLegInput("USD", 10.0, 100.0, 2.5, 0.0, isHedged = false)
            )
        )
        assertEquals(110000.0, kztToUsd.totalRepaymentInBaseUnhedged, 0.5)

        val domesticOnly = MultiCurrencyCreditEngine.calculate(
            dealAmount = 50000.0,
            baseCurrencyCode = "BYN",
            tenureMonths = 12,
            legsInput = listOf(
                CurrencyCreditLegInput("BYN", 12.0, 100.0, 3.0, 10.0, isHedged = true)
            )
        )
        assertEquals(0.0, domesticOnly.totalHedgeCostInBase, 0.01)
        assertEquals(domesticOnly.totalRepaymentInBaseHedged, domesticOnly.totalRepaymentInBaseUnhedged, 0.01)

        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val session = com.example.ui.viewmodel.DealSessionState(scope)
        val controller = com.example.ui.viewmodel.FinancingController(session)
        session.selectActiveCurrency("BYN", 120000.0)
        controller.updateMultiCreditCalculations()
        assertEquals("BYN", controller.multiCreditResult.value.baseCurrency)
        assertEquals("Br", controller.multiCreditResult.value.baseCurrencySymbol)
    }

    @Test
    fun `test live fx ranking auto best vs manual point and session sync`() {
        val now = 1_700_000_000_000L
        val nbrbPoint = com.example.data.repository.FxRateSourcePoint(
            id = "nbrb_official",
            country = com.example.data.repository.FxCountryRegion.BY,
            providerNameEn = "NBRB",
            providerNameRu = "НБРБ",
            pointDetailsEn = "Official",
            pointDetailsRu = "Официальный",
            ratesToUsd = mapOf("USD" to 1.0, "BYN" to 3.25, "EUR" to 0.92),
            fetchedAtMillis = now,
            isCentralBank = true
        )
        val bankPointBest = com.example.data.repository.FxRateSourcePoint(
            id = "belarusbank_best",
            country = com.example.data.repository.FxCountryRegion.BY,
            providerNameEn = "Belarusbank #1",
            providerNameRu = "Беларусбанк №1",
            pointDetailsEn = "Minsk",
            pointDetailsRu = "Минск",
            ratesToUsd = mapOf("USD" to 1.0, "BYN" to 3.31, "EUR" to 0.94),
            fetchedAtMillis = now,
            isCentralBank = false
        )

        val rankedUsdByn = com.example.data.repository.LiveFxRatesService.rankPointsForPair(
            points = listOf(nbrbPoint, bankPointBest),
            baseCode = "USD",
            targetCode = "BYN",
            region = com.example.data.repository.FxCountryRegion.BY
        )
        assertEquals("belarusbank_best", rankedUsdByn.first().id)
        assertEquals(
            "nbrb_official",
            com.example.data.repository.LiveFxRatesService.findCentralBankPoint(
                listOf(bankPointBest, nbrbPoint),
                com.example.data.repository.FxCountryRegion.BY
            )?.id
        )

        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val session = com.example.ui.viewmodel.DealSessionState(scope)
        val controller = com.example.ui.viewmodel.FinancingController(session)
        session.isClientCountryEnabled.value = true
        session.fxCountryRegion.value = com.example.data.repository.FxCountryRegion.BY
        session.fxAvailablePoints.value = listOf(nbrbPoint, bankPointBest)
        session.targetCurrency.value = "BYN"

        // Market/Bank Auto mode selects best USD -> BYN rate (3.31)
        session.fxRateMode.value = com.example.data.repository.FxRateMode.MARKET_BANK
        val autoChosen = session.applyBestOrSelectedFxPoint(1000.0)
        assertEquals("belarusbank_best", autoChosen?.id)
        assertEquals(3310.0, session.convertedDealValue.value, 0.01)

        // Switching mode to Central Bank picks NBRB (3.25)
        session.fxRateMode.value = com.example.data.repository.FxRateMode.CENTRAL_BANK
        val cbChosen = session.applyBestOrSelectedFxPoint(1000.0)
        assertEquals("nbrb_official", cbChosen?.id)
        assertEquals(3250.0, session.convertedDealValue.value, 0.01)

        // Manual mode switches to Belarusbank (3.31)
        val manualChosen = session.selectManualFxPoint("belarusbank_best", 1000.0)
        assertEquals("belarusbank_best", manualChosen?.id)
        assertFalse(session.isFxAutoBestEnabled.value)
        assertEquals(3310.0, session.convertedDealValue.value, 0.01)

        // Credit engine reflects updated EUR rate (0.94 from Belarusbank)
        controller.updateMultiCreditCalculations()
        val eurLeg = controller.multiCreditResult.value.legs.first { it.currencyCode == "EUR" }
        assertEquals(0.94, eurLeg.spotCrossRate, 0.001)
    }

    @Test
    fun `test workspace draft storage saves restores and recalculates pre-meeting state`() {
        val store = com.example.data.repository.InMemoryDraftStore()
        val storage = com.example.data.repository.WorkspaceDraftStorage(store)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

        val session1 = com.example.ui.viewmodel.DealSessionState(scope)
        val financing1 = com.example.ui.viewmodel.FinancingController(session1)
        val pricing1 = com.example.ui.viewmodel.PricingMarginController(session1)
        val workingCapital1 = com.example.ui.viewmodel.WorkingCapitalController(session1) { 5.0 }

        session1.selectedTabIndex.value = 2
        session1.dealTitle.value = "МинскКонтракт 2026"
        session1.clientName.value = "ОАО Глобел"
        session1.appLanguage.value = com.example.ui.i18n.AppLanguage.RU
        session1.isDarkTheme.value = false
        session1.isClientCountryEnabled.value = true
        session1.fxCountryRegion.value = com.example.data.repository.FxCountryRegion.BY
        session1.fxRateMode.value = com.example.data.repository.FxRateMode.CENTRAL_BANK
        session1.targetCurrency.value = "BYN"
        session1.applyFxSourcePoint(
            com.example.data.repository.FxRateSourcePoint(
                id = "nbrb_official",
                country = com.example.data.repository.FxCountryRegion.BY,
                providerNameEn = "NBRB",
                providerNameRu = "НБРБ",
                pointDetailsEn = "Official",
                pointDetailsRu = "Официальный",
                ratesToUsd = mapOf("USD" to 1.0, "BYN" to 3.25, "EUR" to 0.92),
                fetchedAtMillis = 1_700_000_000_000L,
                isCentralBank = true
            ),
            currentPrincipalAmount = 12000.0
        )

        financing1.principalAmount.value = "12000"
        financing1.downPayment.value = "1500"
        financing1.annualInterestRate.value = "16.94"
        financing1.tenureMonths.value = "24"
        financing1.updateFinancing()
        financing1.creditSelectedCurrencies.value = listOf("USD", "EUR")
        financing1.creditAllocations.value = mapOf("USD" to 40.0, "EUR" to 60.0)
        financing1.creditHedgeEnabled.value = false
        financing1.updateMultiCreditCalculations()

        pricing1.waterfallRsp.value = "140"
        pricing1.waterfallBackBonus.value = "12"
        pricing1.updateWaterfallCalculations()

        workingCapital1.termsDays.value = "45"
        workingCapital1.updatePaymentTermsCalculations()

        storage.saveSnapshot(session1, financing1, pricing1, workingCapital1)

        val session2 = com.example.ui.viewmodel.DealSessionState(scope)
        val financing2 = com.example.ui.viewmodel.FinancingController(session2)
        val pricing2 = com.example.ui.viewmodel.PricingMarginController(session2)
        val workingCapital2 = com.example.ui.viewmodel.WorkingCapitalController(session2) { 5.0 }

        val restored = storage.restoreInto(session2, financing2, pricing2, workingCapital2) {
            session2.updateCurrencyConversion(financing2.principalAmount.value.replace(",", "").toDoubleOrNull() ?: 0.0)
            financing2.updateFinancing()
            financing2.updateMultiCreditCalculations()
            pricing2.updateWaterfallCalculations()
            workingCapital2.updatePaymentTermsCalculations()
        }

        assertTrue(restored)
        assertEquals(2, session2.selectedTabIndex.value)
        assertEquals("МинскКонтракт 2026", session2.dealTitle.value)
        assertEquals("ОАО Глобел", session2.clientName.value)
        assertEquals(com.example.ui.i18n.AppLanguage.RU, session2.appLanguage.value)
        assertFalse(session2.isDarkTheme.value)
        assertTrue(session2.isClientCountryEnabled.value)
        assertEquals(com.example.data.repository.FxCountryRegion.BY, session2.fxCountryRegion.value)
        assertEquals(com.example.data.repository.FxRateMode.CENTRAL_BANK, session2.fxRateMode.value)
        assertEquals(session1.convertedDealValue.value, session2.convertedDealValue.value, 0.01)
        assertEquals(financing1.amortization.value.periodicPayment, financing2.amortization.value.periodicPayment, 0.01)
        assertEquals(
            financing1.multiCreditResult.value.totalRepaymentInBaseUnhedged,
            financing2.multiCreditResult.value.totalRepaymentInBaseUnhedged,
            0.01
        )
        assertEquals(pricing1.waterfallResult.value.netNetPrice, pricing2.waterfallResult.value.netNetPrice, 0.01)
        assertEquals(workingCapital1.paymentTermsResult.value.costOfCredit, workingCapital2.paymentTermsResult.value.costOfCredit, 0.01)

        // Disabling remember mode clears draft and prevents restore
        storage.setRememberEnabled(false)
        assertFalse(storage.isRememberEnabled())
        val session3 = com.example.ui.viewmodel.DealSessionState(scope)
        val financing3 = com.example.ui.viewmodel.FinancingController(session3)
        val pricing3 = com.example.ui.viewmodel.PricingMarginController(session3)
        val workingCapital3 = com.example.ui.viewmodel.WorkingCapitalController(session3) { 5.0 }
        assertFalse(storage.restoreInto(session3, financing3, pricing3, workingCapital3))
        assertFalse(session3.isRememberDraftEnabled.value)
        assertEquals("Enterprise Platform & Support Agreement", session3.dealTitle.value)
    }
}
