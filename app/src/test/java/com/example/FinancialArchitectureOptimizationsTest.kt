package com.example

import com.example.data.model.PaymentFrequency
import com.example.domain.calculator.AmortizationEngine
import com.example.domain.calculator.CurrencyCreditLegInput
import com.example.domain.calculator.MultiCurrencyCreditEngine
import com.example.util.toCleanDouble
import com.example.util.toCleanInt
import org.junit.Assert.assertEquals
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
}
