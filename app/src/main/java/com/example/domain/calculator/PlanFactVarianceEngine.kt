package com.example.domain.calculator

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

enum class PlanFactHealthStatus {
    OUTPERFORMING, // 🟢 План перевыполнен
    ACCEPTABLE,    // 🟡 Допустимое отклонение
    EROSION        // 🔴 Эрозия прибыли
}

data class PlanFactResult(
    val planPrice: Double,
    val planVolume: Int,
    val planCost: Double,
    val planDays: Int,
    val actualPrice: Double,
    val actualVolume: Int,
    val actualCost: Double,
    val actualDays: Int,
    val actualPenalties: Double,
    val capitalRatePct: Double,

    // Aggregates
    val planRevenue: Double,
    val planCogs: Double,
    val planGrossProfit: Double,
    val planMarginPct: Double,
    val planDeferralCost: Double,
    val planNetProfit: Double,

    val actualRevenue: Double,
    val actualCogs: Double,
    val actualGrossProfit: Double,
    val actualMarginPct: Double,
    val actualDeferralCost: Double,
    val actualNetProfit: Double,

    // Deltas
    val revenueDelta: Double,
    val revenueDeltaPct: Double,
    val grossProfitDelta: Double,
    val netProfitDelta: Double,
    val netProfitDeltaPct: Double,
    val marginPctDeltaPts: Double,

    // Variance Bridge (Факторный анализ отклонений прибыли)
    val volumeEffectProfit: Double,
    val priceEffectProfit: Double,
    val costEffectProfit: Double,
    val dsoDeferralEffectProfit: Double,
    val penaltiesEffectProfit: Double,

    val status: PlanFactHealthStatus,
    val verdictRu: String,
    val verdictEn: String
)

object PlanFactVarianceEngine {

    private fun r2(value: Double): Double = round(value * 100.0) / 100.0

    /**
     * Conducts standard corporate variance bridge decomposition of Net Profit delta:
     * \Delta Net Profit = Volume Effect + Price Effect + Cost Effect + DSO Deferral Effect + Penalties
     */
    fun calculate(
        planPrice: Double,
        planVolume: Int,
        planCost: Double,
        planDays: Int,
        actualPrice: Double,
        actualVolume: Int,
        actualCost: Double,
        actualDays: Int,
        actualPenalties: Double = 0.0,
        capitalRatePct: Double = 18.0
    ): PlanFactResult {
        val safePlanPrice = max(0.0, planPrice)
        val safePlanVol = max(1, planVolume)
        val safePlanCost = max(0.0, planCost)
        val safePlanDays = max(0, planDays)

        val safeActPrice = max(0.0, actualPrice)
        val safeActVol = max(0, actualVolume)
        val safeActCost = max(0.0, actualCost)
        val safeActDays = max(0, actualDays)
        val safePenalties = max(0.0, actualPenalties)
        val safeRate = max(0.0, capitalRatePct) / 100.0

        // Plan calculations
        val planRev = safePlanPrice * safePlanVol
        val planCogs = safePlanCost * safePlanVol
        val planGross = planRev - planCogs
        val planMargin = if (planRev > 0) (planGross / planRev) * 100.0 else 0.0
        val planDeferral = planRev * safeRate * (safePlanDays.toDouble() / 365.0)
        val planNet = planGross - planDeferral

        // Actual calculations
        val actRev = safeActPrice * safeActVol
        val actCogs = safeActCost * safeActVol
        val actGross = actRev - actCogs
        val actMargin = if (actRev > 0) (actGross / actRev) * 100.0 else 0.0
        val actDeferral = actRev * safeRate * (safeActDays.toDouble() / 365.0)
        val actNet = actGross - actDeferral - safePenalties

        // Deltas
        val revDelta = actRev - planRev
        val revDeltaPct = if (planRev > 0) (revDelta / planRev) * 100.0 else 0.0
        val grossDelta = actGross - planGross
        val netDelta = actNet - planNet
        val netDeltaPct = if (abs(planNet) > 0.001) (netDelta / abs(planNet)) * 100.0 else 0.0
        val marginDeltaPts = actMargin - planMargin

        // 1. Volume Effect: (Q_act - Q_plan) * (P_plan - C_plan)
        val unitPlanGross = safePlanPrice - safePlanCost
        val volumeEffect = (safeActVol - safePlanVol).toDouble() * unitPlanGross

        // 2. Price Effect: (P_act - P_plan) * Q_act
        val priceEffect = (safeActPrice - safePlanPrice) * safeActVol.toDouble()

        // 3. Cost Effect: (C_plan - C_act) * Q_act
        val costEffect = (safePlanCost - safeActCost) * safeActVol.toDouble()

        // 4. Deferral / Working Capital Effect: -(ActDeferral - PlanDeferral)
        val deferralEffect = -(actDeferral - planDeferral)

        // 5. Penalties / Dispute deduction
        val penaltiesEffect = -safePenalties

        val status = when {
            netDelta >= 0.0 -> PlanFactHealthStatus.OUTPERFORMING
            actNet > 0.0 && actMargin >= planMargin * 0.85 -> PlanFactHealthStatus.ACCEPTABLE
            else -> PlanFactHealthStatus.EROSION
        }

        val verdictRu = when (status) {
            PlanFactHealthStatus.OUTPERFORMING -> "План перевыполнен: чистая прибыль выше плановой на +${r2(netDelta)}"
            PlanFactHealthStatus.ACCEPTABLE -> "Умеренное отклонение: маржа в допустимом коридоре (дельта: ${r2(netDelta)})"
            PlanFactHealthStatus.EROSION -> "Эрозия прибыли: фактическая прибыль существенно ниже плана (дельта: ${r2(netDelta)})"
        }

        val verdictEn = when (status) {
            PlanFactHealthStatus.OUTPERFORMING -> "Plan outperformed: net profit exceeded target by +${r2(netDelta)}"
            PlanFactHealthStatus.ACCEPTABLE -> "Acceptable variance: margins within corridor (delta: ${r2(netDelta)})"
            PlanFactHealthStatus.EROSION -> "Profit erosion: actual profit significantly behind plan (delta: ${r2(netDelta)})"
        }

        return PlanFactResult(
            planPrice = r2(safePlanPrice),
            planVolume = safePlanVol,
            planCost = r2(safePlanCost),
            planDays = safePlanDays,
            actualPrice = r2(safeActPrice),
            actualVolume = safeActVol,
            actualCost = r2(safeActCost),
            actualDays = safeActDays,
            actualPenalties = r2(safePenalties),
            capitalRatePct = r2(capitalRatePct),
            planRevenue = r2(planRev),
            planCogs = r2(planCogs),
            planGrossProfit = r2(planGross),
            planMarginPct = r2(planMargin),
            planDeferralCost = r2(planDeferral),
            planNetProfit = r2(planNet),
            actualRevenue = r2(actRev),
            actualCogs = r2(actCogs),
            actualGrossProfit = r2(actGross),
            actualMarginPct = r2(actMargin),
            actualDeferralCost = r2(actDeferral),
            actualNetProfit = r2(actNet),
            revenueDelta = r2(revDelta),
            revenueDeltaPct = r2(revDeltaPct),
            grossProfitDelta = r2(grossDelta),
            netProfitDelta = r2(netDelta),
            netProfitDeltaPct = r2(netDeltaPct),
            marginPctDeltaPts = r2(marginDeltaPts),
            volumeEffectProfit = r2(volumeEffect),
            priceEffectProfit = r2(priceEffect),
            costEffectProfit = r2(costEffect),
            dsoDeferralEffectProfit = r2(deferralEffect),
            penaltiesEffectProfit = r2(penaltiesEffect),
            status = status,
            verdictRu = verdictRu,
            verdictEn = verdictEn
        )
    }
}
