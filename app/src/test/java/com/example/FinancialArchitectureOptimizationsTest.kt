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
}
