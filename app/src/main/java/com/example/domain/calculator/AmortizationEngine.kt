package com.example.domain.calculator

import com.example.data.model.AmortizationRow
import com.example.data.model.AmortizationSummary
import com.example.data.model.PaymentFrequency
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

object AmortizationEngine {

    /**
     * Calculates the periodic payment and generates full amortization schedule,
     * with optional Grace Period (льготный период), two-tier interest rate,
     * and optional Floating Rate (плавающая ставка: бенчмарк + спред с периодическим пересмотром).
     *
     * PMT formula: P * (r * (1 + r)^n) / ((1 + r)^n - 1)
     * where r = periodic interest rate, n = total periods
     */
    fun calculate(
        principal: Double,
        annualInterestRatePct: Double,
        tenureMonths: Int,
        downPayment: Double = 0.0,
        frequency: PaymentFrequency = PaymentFrequency.MONTHLY,
        hasGracePeriod: Boolean = false,
        graceTenureMonths: Int = 0,
        graceInterestRatePct: Double = 0.0,
        postGraceInterestRatePct: Double = annualInterestRatePct,
        // Floating Rate Parameters
        isFloatingRate: Boolean = false,
        floatingBenchmarkRatePct: Double = 0.0,
        floatingSpreadPct: Double = 0.0,
        floatingResetIntervalMonths: Int = 3,
        floatingRateTrendAnnualPct: Double = 0.0,
        // Custom Periodic Payment / Prepayment modeling (Досрочное погашение)
        customPeriodicPayment: Double? = null
    ): AmortizationSummary {
        val netPrincipal = max(0.0, principal - downPayment)
        if (netPrincipal <= 0.0 || tenureMonths <= 0) {
            return AmortizationSummary(
                periodicPayment = 0.0,
                totalPayments = 0.0,
                totalInterest = 0.0,
                schedule = emptyList(),
                frequency = frequency
            )
        }

        val years = tenureMonths / 12.0
        val totalPeriods = max(1, kotlin.math.round(years * frequency.periodsPerYear).toInt())

        // --- 1. FLOATING RATE ENGINE (Плавающая ставка: бенчмарк + спред с пересмотром) ---
        if (isFloatingRate) {
            var currentAnnualRate = max(0.0, floatingBenchmarkRatePct + floatingSpreadPct)
            val resetIntervalPeriods = max(1, kotlin.math.round((floatingResetIntervalMonths / 12.0) * frequency.periodsPerYear).toInt())
            val trendPerReset = floatingRateTrendAnnualPct * (floatingResetIntervalMonths / 12.0)

            var balance = netPrincipal
            val schedule = mutableListOf<AmortizationRow>()
            var accumulatedInterest = 0.0
            var sumRates = 0.0
            var currentPayment = 0.0
            var firstPayment = 0.0

            for (period in 1..totalPeriods) {
                // Update floating rate at reset intervals
                if (period > 1 && (period - 1) % resetIntervalPeriods == 0) {
                    currentAnnualRate = max(0.0, currentAnnualRate + trendPerReset)
                }
                val periodicRate = (currentAnnualRate / 100.0) / frequency.periodsPerYear
                sumRates += currentAnnualRate

                val remainingPeriods = totalPeriods - period + 1
                if (periodicRate <= 0.000001) {
                    currentPayment = balance / remainingPeriods
                } else {
                    val factor = (1.0 + periodicRate).pow(remainingPeriods.toDouble())
                    currentPayment = balance * (periodicRate * factor) / (factor - 1.0)
                }

                if (period == 1) {
                    firstPayment = currentPayment
                }

                val interestPart = if (periodicRate > 0.0) balance * periodicRate else 0.0
                var principalPart = currentPayment - interestPart

                if (period == totalPeriods || principalPart > balance) {
                    principalPart = balance
                }

                balance = max(0.0, balance - principalPart)
                accumulatedInterest += interestPart

                schedule.add(
                    AmortizationRow(
                        period = period,
                        payment = roundTwoDecimals(principalPart + interestPart),
                        principal = roundTwoDecimals(principalPart),
                        interest = roundTwoDecimals(interestPart),
                        remainingBalance = roundTwoDecimals(balance),
                        isGracePeriod = false,
                        rateAppliedPct = roundTwoDecimals(currentAnnualRate)
                    )
                )
            }

            val totalPayments = netPrincipal + accumulatedInterest
            val avgRate = if (totalPeriods > 0) sumRates / totalPeriods else currentAnnualRate

            return AmortizationSummary(
                periodicPayment = roundTwoDecimals(firstPayment),
                totalPayments = roundTwoDecimals(totalPayments),
                totalInterest = roundTwoDecimals(accumulatedInterest),
                schedule = schedule,
                frequency = frequency,
                hasGracePeriod = false,
                gracePeriodsCount = 0,
                gracePeriodicPayment = 0.0,
                postGracePeriodicPayment = roundTwoDecimals(firstPayment),
                graceInterestRatePct = 0.0,
                postGraceInterestRatePct = roundTwoDecimals(currentAnnualRate),
                totalGracePayments = 0.0,
                totalPostGracePayments = roundTwoDecimals(totalPayments),
                isFloatingRate = true,
                floatingBenchmarkRatePct = floatingBenchmarkRatePct,
                floatingSpreadPct = floatingSpreadPct,
                effectiveAverageRatePct = roundTwoDecimals(avgRate)
            )
        }

        // --- 2. FIXED RATE / GRACE PERIOD CALCULATION ---
        val isGraceActive = hasGracePeriod && graceTenureMonths > 0
        val graceYears = graceTenureMonths / 12.0
        val calculatedGracePeriods = if (isGraceActive) {
            max(1, kotlin.math.round(graceYears * frequency.periodsPerYear).toInt()).coerceAtMost(totalPeriods - 1)
        } else {
            0
        }
        val postGracePeriods = totalPeriods - calculatedGracePeriods

        val standardPeriodicRate = (annualInterestRatePct / 100.0) / frequency.periodsPerYear
        val gracePeriodicRate = (graceInterestRatePct / 100.0) / frequency.periodsPerYear
        val postGracePeriodicRate = (postGraceInterestRatePct / 100.0) / frequency.periodsPerYear

        // Single-tier fixed rate calculation
        if (!isGraceActive || calculatedGracePeriods <= 0) {
            val standardPayment = if (standardPeriodicRate <= 0.000001) {
                netPrincipal / totalPeriods
            } else {
                val factor = (1.0 + standardPeriodicRate).pow(totalPeriods.toDouble())
                netPrincipal * (standardPeriodicRate * factor) / (factor - 1.0)
            }

            // Calculate standard baseline interest (without prepayment)
            var baselineInterest = 0.0
            var baseBal = netPrincipal
            for (p in 1..totalPeriods) {
                val intP = if (standardPeriodicRate > 0.0) baseBal * standardPeriodicRate else 0.0
                var princP = standardPayment - intP
                if (p == totalPeriods || princP > baseBal) princP = baseBal
                baseBal = max(0.0, baseBal - princP)
                baselineInterest += intP
            }

            val isAccelerated = customPeriodicPayment != null && customPeriodicPayment > standardPayment

            var balance = netPrincipal
            val schedule = mutableListOf<AmortizationRow>()
            var accumulatedInterest = 0.0
            var period = 1
            val maxPeriods = totalPeriods

            while (balance > 0.0001 && period <= maxPeriods) {
                val interestPart = if (standardPeriodicRate > 0.0) balance * standardPeriodicRate else 0.0
                val targetPayment = if (isAccelerated) customPeriodicPayment!! else standardPayment

                var principalPart = targetPayment - interestPart
                var actualPayment = targetPayment

                if (period == maxPeriods || principalPart >= balance || (balance + interestPart) <= targetPayment) {
                    principalPart = balance
                    actualPayment = principalPart + interestPart
                }

                balance = max(0.0, balance - principalPart)
                accumulatedInterest += interestPart

                schedule.add(
                    AmortizationRow(
                        period = period,
                        payment = roundTwoDecimals(actualPayment),
                        principal = roundTwoDecimals(principalPart),
                        interest = roundTwoDecimals(interestPart),
                        remainingBalance = roundTwoDecimals(balance),
                        isGracePeriod = false,
                        rateAppliedPct = annualInterestRatePct
                    )
                )
                period++
            }

            val totalPayments = netPrincipal + accumulatedInterest
            val actualPeriods = schedule.size
            val periodsSaved = if (isAccelerated) max(0, totalPeriods - actualPeriods) else 0
            val interestSaved = if (isAccelerated) roundTwoDecimals(max(0.0, baselineInterest - accumulatedInterest)) else 0.0

            return AmortizationSummary(
                periodicPayment = roundTwoDecimals(if (isAccelerated) customPeriodicPayment!! else standardPayment),
                totalPayments = roundTwoDecimals(totalPayments),
                totalInterest = roundTwoDecimals(accumulatedInterest),
                schedule = schedule,
                frequency = frequency,
                hasGracePeriod = false,
                gracePeriodsCount = 0,
                gracePeriodicPayment = 0.0,
                postGracePeriodicPayment = roundTwoDecimals(standardPayment),
                graceInterestRatePct = 0.0,
                postGraceInterestRatePct = annualInterestRatePct,
                totalGracePayments = 0.0,
                totalPostGracePayments = roundTwoDecimals(totalPayments),
                isFloatingRate = false,
                effectiveAverageRatePct = annualInterestRatePct,
                customPayment = customPeriodicPayment,
                isAccelerated = isAccelerated,
                acceleratedPeriods = actualPeriods,
                periodsSaved = periodsSaved,
                interestSaved = interestSaved
            )
        }

        // --- TWO-TIER / GRACE PERIOD CALCULATION ---
        // During Grace: Client pays only interest (or 0 if rate is 0%). Principal does not amortize in standard mode.
        val standardGracePayment = if (gracePeriodicRate > 0.0) netPrincipal * gracePeriodicRate else 0.0

        // Post Grace: Balance is netPrincipal amortized over postGracePeriods at postGracePeriodicRate
        val standardPostGracePayment = if (postGracePeriodicRate <= 0.000001) {
            netPrincipal / postGracePeriods
        } else {
            val factor = (1.0 + postGracePeriodicRate).pow(postGracePeriods.toDouble())
            netPrincipal * (postGracePeriodicRate * factor) / (factor - 1.0)
        }

        // Calculate baseline standard interest for comparison
        var baselineInterest = 0.0
        var baseBal = netPrincipal
        for (p in 1..totalPeriods) {
            val isG = p <= calculatedGracePeriods
            val r = if (isG) gracePeriodicRate else postGracePeriodicRate
            val intP = if (r > 0.0) baseBal * r else 0.0
            var princP = if (isG) 0.0 else standardPostGracePayment - intP
            if (p == totalPeriods || princP > baseBal) princP = baseBal
            baseBal = max(0.0, baseBal - princP)
            baselineInterest += intP
        }

        val hasCustomPayment = customPeriodicPayment != null && customPeriodicPayment > 0.0

        var balance = netPrincipal
        val schedule = mutableListOf<AmortizationRow>()
        var accumulatedInterest = 0.0
        var totalGracePaid = 0.0
        var totalPostGracePaid = 0.0
        var period = 1

        while (balance > 0.0001 && period <= totalPeriods) {
            val isGrace = period <= calculatedGracePeriods
            val appliedRatePct = if (isGrace) graceInterestRatePct else postGraceInterestRatePct
            val activeRate = if (isGrace) gracePeriodicRate else postGracePeriodicRate

            val interestPart = if (activeRate > 0.0) balance * activeRate else 0.0
            var principalPart: Double
            var currentPayment: Double

            if (isGrace) {
                if (hasCustomPayment && customPeriodicPayment!! > interestPart) {
                    val excess = customPeriodicPayment - interestPart
                    principalPart = minOf(balance, excess)
                    currentPayment = interestPart + principalPart
                    balance = max(0.0, balance - principalPart)
                } else {
                    principalPart = 0.0
                    currentPayment = interestPart
                }
                totalGracePaid += currentPayment
            } else {
                val targetPayment = if (hasCustomPayment) maxOf(standardPostGracePayment, customPeriodicPayment!!) else standardPostGracePayment
                if (period == totalPeriods || (balance + interestPart) <= targetPayment) {
                    principalPart = balance
                    currentPayment = principalPart + interestPart
                } else {
                    principalPart = targetPayment - interestPart
                    currentPayment = targetPayment
                }
                balance = max(0.0, balance - principalPart)
                totalPostGracePaid += currentPayment
            }

            accumulatedInterest += interestPart

            schedule.add(
                AmortizationRow(
                    period = period,
                    payment = roundTwoDecimals(currentPayment),
                    principal = roundTwoDecimals(principalPart),
                    interest = roundTwoDecimals(interestPart),
                    remainingBalance = roundTwoDecimals(balance),
                    isGracePeriod = isGrace,
                    rateAppliedPct = appliedRatePct
                )
            )
            period++
        }

        val totalPayments = totalGracePaid + totalPostGracePaid
        val actualPeriods = schedule.size
        val periodsSaved = if (hasCustomPayment) max(0, totalPeriods - actualPeriods) else 0
        val interestSaved = if (hasCustomPayment) roundTwoDecimals(max(0.0, baselineInterest - accumulatedInterest)) else 0.0
        val isAccelerated = hasCustomPayment && (periodsSaved > 0 || interestSaved > 0.5)

        return AmortizationSummary(
            periodicPayment = roundTwoDecimals(standardPostGracePayment),
            totalPayments = roundTwoDecimals(totalPayments),
            totalInterest = roundTwoDecimals(accumulatedInterest),
            schedule = schedule,
            frequency = frequency,
            hasGracePeriod = true,
            gracePeriodsCount = calculatedGracePeriods,
            gracePeriodicPayment = roundTwoDecimals(standardGracePayment),
            postGracePeriodicPayment = roundTwoDecimals(standardPostGracePayment),
            graceInterestRatePct = graceInterestRatePct,
            postGraceInterestRatePct = postGraceInterestRatePct,
            totalGracePayments = roundTwoDecimals(totalGracePaid),
            totalPostGracePayments = roundTwoDecimals(totalPostGracePaid),
            isFloatingRate = false,
            effectiveAverageRatePct = roundTwoDecimals((graceInterestRatePct * calculatedGracePeriods + postGraceInterestRatePct * postGracePeriods) / totalPeriods),
            customPayment = customPeriodicPayment,
            isAccelerated = isAccelerated,
            acceleratedPeriods = actualPeriods,
            periodsSaved = periodsSaved,
            interestSaved = interestSaved
        )
    }

    private fun roundTwoDecimals(value: Double): Double {
        return round(value * 100.0) / 100.0
    }

    /**
     * Financial & mathematical evaluation of Credit Prolongation / Restructuring:
     * Compares original tenure vs prolonged tenure (month-to-month, week-to-week, etc.)
     * Measures installment cashflow relief vs cumulative additional interest burden.
     */
    fun analyzeProlongation(
        principal: Double,
        downPayment: Double = 0.0,
        originalTenureMonths: Int,
        prolongedTenureMonths: Int,
        annualInterestRatePct: Double,
        prolongedInterestRatePct: Double = annualInterestRatePct,
        frequency: PaymentFrequency = PaymentFrequency.MONTHLY
    ): CreditProlongationAnalysis {
        val orig = calculate(
            principal = principal,
            annualInterestRatePct = annualInterestRatePct,
            tenureMonths = originalTenureMonths,
            downPayment = downPayment,
            frequency = frequency
        )
        val prolonged = calculate(
            principal = principal,
            annualInterestRatePct = prolongedInterestRatePct,
            tenureMonths = prolongedTenureMonths,
            downPayment = downPayment,
            frequency = frequency
        )

        val paymentRelief = max(0.0, orig.periodicPayment - prolonged.periodicPayment)
        val paymentReliefPct = if (orig.periodicPayment > 0.0) (paymentRelief / orig.periodicPayment) * 100.0 else 0.0

        val extraInterest = max(0.0, prolonged.totalInterest - orig.totalInterest)
        val interestIncreasePct = if (orig.totalInterest > 0.0) (extraInterest / orig.totalInterest) * 100.0 else 0.0

        return CreditProlongationAnalysis(
            originalTenureMonths = max(1, originalTenureMonths),
            prolongedTenureMonths = max(1, prolongedTenureMonths),
            extensionMonths = max(0, prolongedTenureMonths - originalTenureMonths),
            frequency = frequency,
            principal = roundTwoDecimals(max(0.0, principal - downPayment)),
            annualRatePct = roundTwoDecimals(annualInterestRatePct),
            prolongedRatePct = roundTwoDecimals(prolongedInterestRatePct),
            originalPeriodicPayment = orig.periodicPayment,
            prolongedPeriodicPayment = prolonged.periodicPayment,
            periodicPaymentRelief = roundTwoDecimals(paymentRelief),
            periodicPaymentReliefPct = roundTwoDecimals(paymentReliefPct),
            originalTotalInterest = orig.totalInterest,
            prolongedTotalInterest = prolonged.totalInterest,
            additionalInterestCost = roundTwoDecimals(extraInterest),
            interestCostIncreasePct = roundTwoDecimals(interestIncreasePct),
            originalTotalRepayments = orig.totalPayments,
            prolongedTotalRepayments = prolonged.totalPayments
        )
    }
}

data class CreditProlongationAnalysis(
    val originalTenureMonths: Int,
    val prolongedTenureMonths: Int,
    val extensionMonths: Int,
    val frequency: PaymentFrequency,
    val principal: Double,
    val annualRatePct: Double,
    val prolongedRatePct: Double,
    val originalPeriodicPayment: Double,
    val prolongedPeriodicPayment: Double,
    val periodicPaymentRelief: Double,
    val periodicPaymentReliefPct: Double,
    val originalTotalInterest: Double,
    val prolongedTotalInterest: Double,
    val additionalInterestCost: Double,
    val interestCostIncreasePct: Double,
    val originalTotalRepayments: Double,
    val prolongedTotalRepayments: Double
)
