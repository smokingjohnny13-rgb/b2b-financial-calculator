package com.example.domain.calculator

import com.example.data.model.AmortizationRow
import com.example.data.model.AmortizationScheme
import com.example.data.model.AmortizationSummary
import com.example.data.model.PaymentFrequency
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

object AmortizationEngine {

    /**
     * Solves the true Actuarial Internal Rate of Return (IRR / ПСК / Effective APR)
     * and compounded Effective Annual Rate (EAR) from exact cash flows using Bisection.
     * Cash flow at t=0: +principal
     * Cash flow at t=1..N: -payment_t
     * Equation: principal - \sum_{t=1}^N payment_t / (1 + r)^t = 0
     */
    fun calculateIrr(principal: Double, payments: List<Double>, periodsPerYear: Int): Pair<Double, Double> {
        if (principal <= 0.0 || payments.isEmpty()) return Pair(0.0, 0.0)
        val totalPayments = payments.sum()
        if (totalPayments <= principal) return Pair(0.0, 0.0)

        var low = 0.0
        var high = 5.0 // up to 500% periodic rate
        var periodicRate = 0.0

        for (iter in 0 until 40) {
            val mid = (low + high) / 2.0
            var npv = principal
            var discountFactor = 1.0
            val factorStep = 1.0 + mid

            for (payment in payments) {
                discountFactor *= factorStep
                npv -= payment / discountFactor
            }

            if (npv > 0.0) {
                high = mid
            } else {
                low = mid
            }
            periodicRate = mid
        }

        val nominalApr = roundTwoDecimals(periodicRate * periodsPerYear * 100.0)
        val compoundEar = roundTwoDecimals(((1.0 + periodicRate).pow(periodsPerYear.toDouble()) - 1.0) * 100.0)
        return Pair(nominalApr, compoundEar)
    }

    /**
     * Calculates the periodic payment and generates full amortization schedule,
     * supporting both Annuity (равные платежи) and Differentiated (равные доли основного долга),
     * with optional Grace Period (льготный период), two-tier interest rate,
     * and optional Floating Rate (плавающая ставка: бенчмарк + спред с периодическим пересмотром).
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
        scheme: AmortizationScheme = AmortizationScheme.ANNUITY,
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
            var firstPayment = 0.0
            var lastPayment = 0.0
            var period = 1

            val hasPrepayment = customPeriodicPayment != null && customPeriodicPayment > 0.0

            while (balance > 0.0001 && period <= totalPeriods) {
                if (period > 1 && (period - 1) % resetIntervalPeriods == 0) {
                    currentAnnualRate = max(0.0, currentAnnualRate + trendPerReset)
                }
                val periodicRate = (currentAnnualRate / 100.0) / frequency.periodsPerYear
                sumRates += currentAnnualRate

                val remainingPeriods = totalPeriods - period + 1
                val standardPeriodicPayment = if (scheme == AmortizationScheme.DIFFERENTIATED) {
                    val basePrincipalPart = balance / remainingPeriods
                    val interestPart = if (periodicRate > 0.0) balance * periodicRate else 0.0
                    basePrincipalPart + interestPart
                } else {
                    if (periodicRate <= 0.000001) {
                        balance / remainingPeriods
                    } else {
                        val factor = (1.0 + periodicRate).pow(remainingPeriods.toDouble())
                        balance * (periodicRate * factor) / (factor - 1.0)
                    }
                }

                if (period == 1) firstPayment = standardPeriodicPayment

                val interestPart = if (periodicRate > 0.0) balance * periodicRate else 0.0
                val targetPayment = if (hasPrepayment) maxOf(standardPeriodicPayment, customPeriodicPayment!!) else standardPeriodicPayment

                var principalPart = targetPayment - interestPart
                var actualPayment = targetPayment

                if (period == totalPeriods || principalPart >= balance || (balance + interestPart) <= targetPayment) {
                    principalPart = balance
                    actualPayment = principalPart + interestPart
                }

                balance = max(0.0, balance - principalPart)
                accumulatedInterest += interestPart
                lastPayment = actualPayment

                schedule.add(
                    AmortizationRow(
                        period = period,
                        payment = roundTwoDecimals(actualPayment),
                        principal = roundTwoDecimals(principalPart),
                        interest = roundTwoDecimals(interestPart),
                        remainingBalance = roundTwoDecimals(balance),
                        isGracePeriod = false,
                        rateAppliedPct = roundTwoDecimals(currentAnnualRate)
                    )
                )
                period++
            }

            val totalPayments = netPrincipal + accumulatedInterest
            val actualPeriods = schedule.size
            val periodsSaved = if (hasPrepayment) max(0, totalPeriods - actualPeriods) else 0
            val (trueApr, trueEar) = calculateIrr(netPrincipal, schedule.map { it.payment }, frequency.periodsPerYear)
            val avgRate = if (actualPeriods > 0) sumRates / actualPeriods else currentAnnualRate

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
                effectiveAverageRatePct = roundTwoDecimals(avgRate),
                customPayment = customPeriodicPayment,
                isAccelerated = periodsSaved > 0,
                acceleratedPeriods = actualPeriods,
                periodsSaved = periodsSaved,
                interestSaved = 0.0,
                scheme = scheme,
                firstPeriodicPayment = roundTwoDecimals(firstPayment),
                lastPeriodicPayment = roundTwoDecimals(lastPayment),
                trueEffectiveAprPct = trueApr,
                effectiveAnnualRatePct = trueEar
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
            val standardPaymentAnnuity = if (standardPeriodicRate <= 0.000001) {
                netPrincipal / totalPeriods
            } else {
                val factor = (1.0 + standardPeriodicRate).pow(totalPeriods.toDouble())
                netPrincipal * (standardPeriodicRate * factor) / (factor - 1.0)
            }
            val basePrincipalChunk = netPrincipal / totalPeriods

            // Calculate standard baseline interest (without prepayment) according to scheme
            var baselineInterest = 0.0
            var baseBal = netPrincipal
            var baselineFirstPayment = 0.0
            var baselineLastPayment = 0.0
            for (p in 1..totalPeriods) {
                val intP = if (standardPeriodicRate > 0.0) baseBal * standardPeriodicRate else 0.0
                var princP = if (scheme == AmortizationScheme.DIFFERENTIATED) basePrincipalChunk else standardPaymentAnnuity - intP
                if (p == totalPeriods || princP > baseBal) princP = baseBal
                baseBal = max(0.0, baseBal - princP)
                baselineInterest += intP
                val pmt = princP + intP
                if (p == 1) baselineFirstPayment = pmt
                if (p == totalPeriods) baselineLastPayment = pmt
            }

            val requiredMinPayment = if (scheme == AmortizationScheme.DIFFERENTIATED) baselineFirstPayment else standardPaymentAnnuity
            val isAccelerated = customPeriodicPayment != null && customPeriodicPayment > requiredMinPayment

            var balance = netPrincipal
            val schedule = mutableListOf<AmortizationRow>()
            var accumulatedInterest = 0.0
            var period = 1
            val maxPeriods = totalPeriods
            var firstPmt = 0.0
            var lastPmt = 0.0

            while (balance > 0.0001 && period <= maxPeriods) {
                val interestPart = if (standardPeriodicRate > 0.0) balance * standardPeriodicRate else 0.0
                val standardScheduledPayment = if (scheme == AmortizationScheme.DIFFERENTIATED) {
                    basePrincipalChunk + interestPart
                } else {
                    standardPaymentAnnuity
                }

                val targetPayment = if (isAccelerated) customPeriodicPayment!! else standardScheduledPayment
                var principalPart = targetPayment - interestPart
                var actualPayment = targetPayment

                if (period == maxPeriods || principalPart >= balance || (balance + interestPart) <= targetPayment) {
                    principalPart = balance
                    actualPayment = principalPart + interestPart
                }

                balance = max(0.0, balance - principalPart)
                accumulatedInterest += interestPart
                if (period == 1) firstPmt = actualPayment
                lastPmt = actualPayment

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
            val (trueApr, trueEar) = calculateIrr(netPrincipal, schedule.map { it.payment }, frequency.periodsPerYear)

            return AmortizationSummary(
                periodicPayment = roundTwoDecimals(if (isAccelerated) customPeriodicPayment!! else (if (scheme == AmortizationScheme.DIFFERENTIATED) firstPmt else standardPaymentAnnuity)),
                totalPayments = roundTwoDecimals(totalPayments),
                totalInterest = roundTwoDecimals(accumulatedInterest),
                schedule = schedule,
                frequency = frequency,
                hasGracePeriod = false,
                gracePeriodsCount = 0,
                gracePeriodicPayment = 0.0,
                postGracePeriodicPayment = roundTwoDecimals(if (scheme == AmortizationScheme.DIFFERENTIATED) firstPmt else standardPaymentAnnuity),
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
                interestSaved = interestSaved,
                scheme = scheme,
                firstPeriodicPayment = roundTwoDecimals(firstPmt),
                lastPeriodicPayment = roundTwoDecimals(lastPmt),
                trueEffectiveAprPct = trueApr,
                effectiveAnnualRatePct = trueEar
            )
        }

        // --- TWO-TIER / GRACE PERIOD CALCULATION ---
        val standardGracePayment = if (gracePeriodicRate > 0.0) netPrincipal * gracePeriodicRate else 0.0

        val standardPostGracePaymentAnnuity = if (postGracePeriodicRate <= 0.000001) {
            netPrincipal / postGracePeriods
        } else {
            val factor = (1.0 + postGracePeriodicRate).pow(postGracePeriods.toDouble())
            netPrincipal * (postGracePeriodicRate * factor) / (factor - 1.0)
        }
        val basePostGracePrincipalChunk = netPrincipal / postGracePeriods

        // Calculate baseline standard interest for comparison
        var baselineInterest = 0.0
        var baseBal = netPrincipal
        for (p in 1..totalPeriods) {
            val isG = p <= calculatedGracePeriods
            val r = if (isG) gracePeriodicRate else postGracePeriodicRate
            val intP = if (r > 0.0) baseBal * r else 0.0
            var princP = if (isG) {
                0.0
            } else {
                if (scheme == AmortizationScheme.DIFFERENTIATED) basePostGracePrincipalChunk else standardPostGracePaymentAnnuity - intP
            }
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
        var firstPmt = 0.0
        var lastPmt = 0.0
        var firstPostGracePmt = 0.0

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
                val remainingPostGrace = totalPeriods - period + 1
                val standardPostGraceScheduled = if (scheme == AmortizationScheme.DIFFERENTIATED) {
                    (balance / remainingPostGrace) + interestPart
                } else {
                    if (activeRate <= 0.000001) balance / remainingPostGrace
                    else {
                        val factor = (1.0 + activeRate).pow(remainingPostGrace.toDouble())
                        balance * (activeRate * factor) / (factor - 1.0)
                    }
                }
                if (firstPostGracePmt == 0.0) firstPostGracePmt = standardPostGraceScheduled

                val targetPayment = if (hasCustomPayment) maxOf(standardPostGraceScheduled, customPeriodicPayment!!) else standardPostGraceScheduled
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
            if (period == 1) firstPmt = currentPayment
            lastPmt = currentPayment

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

        val (trueApr, trueEar) = calculateIrr(netPrincipal, schedule.map { it.payment }, frequency.periodsPerYear)

        return AmortizationSummary(
            periodicPayment = roundTwoDecimals(if (firstPostGracePmt > 0.0) firstPostGracePmt else standardPostGracePaymentAnnuity),
            totalPayments = roundTwoDecimals(totalPayments),
            totalInterest = roundTwoDecimals(accumulatedInterest),
            schedule = schedule,
            frequency = frequency,
            hasGracePeriod = true,
            gracePeriodsCount = calculatedGracePeriods,
            gracePeriodicPayment = roundTwoDecimals(standardGracePayment),
            postGracePeriodicPayment = roundTwoDecimals(if (firstPostGracePmt > 0.0) firstPostGracePmt else standardPostGracePaymentAnnuity),
            graceInterestRatePct = graceInterestRatePct,
            postGraceInterestRatePct = postGraceInterestRatePct,
            totalGracePayments = roundTwoDecimals(totalGracePaid),
            totalPostGracePayments = roundTwoDecimals(totalPostGracePaid),
            isFloatingRate = false,
            effectiveAverageRatePct = trueApr, // True Actuarial IRR replaces simplified weighted average!
            customPayment = customPeriodicPayment,
            isAccelerated = isAccelerated,
            acceleratedPeriods = actualPeriods,
            periodsSaved = periodsSaved,
            interestSaved = interestSaved,
            scheme = scheme,
            firstPeriodicPayment = roundTwoDecimals(firstPmt),
            lastPeriodicPayment = roundTwoDecimals(lastPmt),
            trueEffectiveAprPct = trueApr,
            effectiveAnnualRatePct = trueEar
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
        frequency: PaymentFrequency = PaymentFrequency.MONTHLY,
        scheme: AmortizationScheme = AmortizationScheme.ANNUITY
    ): CreditProlongationAnalysis {
        val orig = calculate(
            principal = principal,
            annualInterestRatePct = annualInterestRatePct,
            tenureMonths = originalTenureMonths,
            downPayment = downPayment,
            frequency = frequency,
            scheme = scheme
        )
        val prolonged = calculate(
            principal = principal,
            annualInterestRatePct = prolongedInterestRatePct,
            tenureMonths = prolongedTenureMonths,
            downPayment = downPayment,
            frequency = frequency,
            scheme = scheme
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
