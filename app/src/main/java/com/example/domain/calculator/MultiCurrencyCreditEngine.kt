package com.example.domain.calculator

import com.example.data.model.CurrencyRate
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

data class CurrencyCreditLegInput(
    val currencyCode: String,
    val annualRatePct: Double,
    val allocationPct: Double,
    val hedgeCostAnnualPct: Double = 2.5,
    val expectedFxShiftPct: Double = 5.0, // Expected change of FX rate on payment day (+% = base weakens / debt currency appreciates)
    val isHedged: Boolean = true
)

data class CurrencyCreditLegResult(
    val currencyCode: String,
    val currencySymbol: String,
    val spotCrossRate: Double, // 1 Base Currency = X Target Currency
    val allocationPct: Double,
    val principalInBase: Double,
    val principalInCurrency: Double,
    val annualRatePct: Double,
    val interestCostInCurrency: Double,
    val totalRepaymentInCurrency: Double,
    val totalRepaymentInBaseHedged: Double,
    val totalRepaymentInBaseUnhedged: Double,
    val hedgeCostInBase: Double,
    val fxRiskDeltaInBase: Double, // Unhedged - Hedged
    val isHedgeProfitable: Boolean,
    val isHedged: Boolean
)

data class MultiCurrencyCreditResult(
    val dealAmountInBase: Double,
    val baseCurrency: String,
    val baseCurrencySymbol: String,
    val tenureMonths: Int,
    val legs: List<CurrencyCreditLegResult>,
    val totalPrincipalInBase: Double,
    val totalRepaymentInBaseHedged: Double,
    val totalRepaymentInBaseUnhedged: Double,
    val totalHedgeCostInBase: Double,
    val totalFxRiskDeltaInBase: Double,
    val weightedInterestRatePct: Double,
    val weightedEffectiveRateWithHedgePct: Double,
    val isHedgeAdvantageous: Boolean
)

object MultiCurrencyCreditEngine {

    private fun r2(value: Double): Double = round(value * 100.0) / 100.0
    private fun r4(value: Double): Double = round(value * 10000.0) / 10000.0

    /**
     * Default indicative borrowing rates for popular trade currencies.
     */
    fun getDefaultRate(code: String): Double = when (code.uppercase()) {
        "USD" -> 7.5
        "EUR" -> 5.5
        "CNY" -> 4.2
        "RUB" -> 18.5
        "BYN" -> 11.5
        "KZT" -> 14.5
        "AED" -> 6.0
        "TRY" -> 42.0
        "PLN" -> 6.8
        "GBP" -> 6.5
        else -> 8.0
    }

    /**
     * Calculates multi-currency credit financing across 1 to 4 selected currencies
     * with cross-rate conversion and FX hedging on payment/purchase day.
     */
    fun calculate(
        dealAmount: Double,
        baseCurrencyCode: String,
        tenureMonths: Int,
        legsInput: List<CurrencyCreditLegInput>,
        rates: List<CurrencyRate> = CurrencyEngine.DEFAULT_CURRENCIES
    ): MultiCurrencyCreditResult {
        val safeAmount = max(1.0, dealAmount)
        val safeTenure = max(1, tenureMonths)
        val tenureYears = safeTenure.toDouble() / 12.0

        val baseCurr = CurrencyEngine.findByCode(baseCurrencyCode, rates)

        // Sanitize legs (up to 4)
        val selectedLegs = legsInput.take(4).ifEmpty {
            listOf(CurrencyCreditLegInput(currencyCode = baseCurrencyCode, annualRatePct = getDefaultRate(baseCurrencyCode), allocationPct = 100.0))
        }

        // Normalize allocation shares so they sum to 1.0 (100%)
        val totalAllocInput = selectedLegs.sumOf { it.allocationPct }
        val normalizedShares = if (totalAllocInput > 0.0) {
            selectedLegs.map { it.allocationPct / totalAllocInput }
        } else {
            val equalShare = 1.0 / selectedLegs.size
            selectedLegs.map { equalShare }
        }

        val calculatedLegs = mutableListOf<CurrencyCreditLegResult>()
        var sumRepaymentHedged = 0.0
        var sumRepaymentUnhedged = 0.0
        var sumHedgeCost = 0.0
        var sumWeightedRate = 0.0
        var sumWeightedRateHedged = 0.0

        for (i in selectedLegs.indices) {
            val input = selectedLegs[i]
            val share = normalizedShares[i]
            val legCurr = CurrencyEngine.findByCode(input.currencyCode, rates)

            // Cross-rate: how many units of legCurr per 1 baseCurr
            val spotRate = CurrencyEngine.getCrossRate(baseCurrencyCode, input.currencyCode, rates)

            val legPrincipalInBase = safeAmount * share
            val legPrincipalInCurrency = legPrincipalInBase * spotRate

            val borrowRate = max(0.0, input.annualRatePct) / 100.0
            val hedgeCostRate = max(0.0, input.hedgeCostAnnualPct) / 100.0

            // Interest calculation:
            // For standard trade finance (tenure <= 1.0 year), simple linear interest P * r * t matches commercial bank conventions.
            // For prolonged facilities (tenure > 1.0 year), compound capitalization P * ((1 + r)^t - 1) accurately reflects prolonged cost.
            val interestInCurrency = if (tenureYears <= 1.0) {
                legPrincipalInCurrency * borrowRate * tenureYears
            } else {
                legPrincipalInCurrency * ((1.0 + borrowRate).pow(tenureYears) - 1.0)
            }
            val totalRepaymentInCurrency = legPrincipalInCurrency + interestInCurrency

            // 1. Hedged Repayment:
            // Hedging fixes forward cross-rate on payment day, protecting completely against currency depreciation.
            // Effective cost = Borrow Rate + Hedge Premium (annualized)
            val effectiveHedgedRate = borrowRate + (if (input.isHedged && !input.currencyCode.equals(baseCurrencyCode, ignoreCase = true)) hedgeCostRate else 0.0)
            val totalRepaymentInBaseHedged = if (tenureYears <= 1.0) {
                legPrincipalInBase * (1.0 + effectiveHedgedRate * tenureYears)
            } else {
                legPrincipalInBase * (1.0 + effectiveHedgedRate).pow(tenureYears)
            }
            val hedgeCostInBase = if (input.isHedged && !input.currencyCode.equals(baseCurrencyCode, ignoreCase = true)) {
                if (tenureYears <= 1.0) {
                    legPrincipalInBase * hedgeCostRate * tenureYears
                } else {
                    legPrincipalInBase * ((1.0 + hedgeCostRate).pow(tenureYears) - 1.0)
                }
            } else {
                0.0
            }

            // 2. Unhedged Repayment:
            // Time-horizon compounded FX shift:
            // Expected FX shift is annualized (% per annum). Over tenureYears, cumulative shift is (1 + shift)^t - 1.
            val annualFxShift = input.expectedFxShiftPct / 100.0
            val cumulativeFxShift = if (tenureYears > 0.0) {
                (1.0 + annualFxShift).pow(tenureYears) - 1.0
            } else {
                0.0
            }

            val endRateBasePerLeg = if (spotRate > 0.0) {
                val basePerUnitStart = 1.0 / spotRate
                basePerUnitStart * (1.0 + cumulativeFxShift)
            } else {
                1.0
            }

            val totalRepaymentInBaseUnhedged = if (input.currencyCode.equals(baseCurrencyCode, ignoreCase = true)) {
                if (tenureYears <= 1.0) {
                    legPrincipalInBase * (1.0 + borrowRate * tenureYears)
                } else {
                    legPrincipalInBase * (1.0 + borrowRate).pow(tenureYears)
                }
            } else {
                totalRepaymentInCurrency * endRateBasePerLeg
            }

            val fxRiskDeltaInBase = totalRepaymentInBaseUnhedged - totalRepaymentInBaseHedged
            val isHedgeProfitable = fxRiskDeltaInBase > 0.0

            sumRepaymentHedged += totalRepaymentInBaseHedged
            sumRepaymentUnhedged += totalRepaymentInBaseUnhedged
            sumHedgeCost += hedgeCostInBase
            sumWeightedRate += (input.annualRatePct * share)
            sumWeightedRateHedged += ((effectiveHedgedRate * 100.0) * share)

            calculatedLegs.add(
                CurrencyCreditLegResult(
                    currencyCode = input.currencyCode,
                    currencySymbol = legCurr.symbol,
                    spotCrossRate = r4(spotRate),
                    allocationPct = r2(share * 100.0),
                    principalInBase = r2(legPrincipalInBase),
                    principalInCurrency = r2(legPrincipalInCurrency),
                    annualRatePct = r2(input.annualRatePct),
                    interestCostInCurrency = r2(interestInCurrency),
                    totalRepaymentInCurrency = r2(totalRepaymentInCurrency),
                    totalRepaymentInBaseHedged = r2(totalRepaymentInBaseHedged),
                    totalRepaymentInBaseUnhedged = r2(totalRepaymentInBaseUnhedged),
                    hedgeCostInBase = r2(hedgeCostInBase),
                    fxRiskDeltaInBase = r2(fxRiskDeltaInBase),
                    isHedgeProfitable = isHedgeProfitable,
                    isHedged = input.isHedged
                )
            )
        }

        val totalFxDelta = sumRepaymentUnhedged - sumRepaymentHedged

        return MultiCurrencyCreditResult(
            dealAmountInBase = r2(safeAmount),
            baseCurrency = baseCurrencyCode,
            baseCurrencySymbol = baseCurr.symbol,
            tenureMonths = safeTenure,
            legs = calculatedLegs,
            totalPrincipalInBase = r2(safeAmount),
            totalRepaymentInBaseHedged = r2(sumRepaymentHedged),
            totalRepaymentInBaseUnhedged = r2(sumRepaymentUnhedged),
            totalHedgeCostInBase = r2(sumHedgeCost),
            totalFxRiskDeltaInBase = r2(totalFxDelta),
            weightedInterestRatePct = r2(sumWeightedRate),
            weightedEffectiveRateWithHedgePct = r2(sumWeightedRateHedged),
            isHedgeAdvantageous = totalFxDelta > 0.0
        )
    }
}
