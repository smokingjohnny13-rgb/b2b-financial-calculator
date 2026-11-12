package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.calculator.PlanFactHealthStatus
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExecutiveBorder
import com.example.ui.theme.ExecutiveCard
import com.example.ui.theme.ExecutiveSurface
import com.example.ui.theme.ExecutiveSurfaceVariant
import com.example.ui.theme.GoldAccent
import com.example.ui.viewmodel.BigDealViewModel

@Composable
fun PlanFactVarianceCard(
    viewModel: BigDealViewModel,
    modifier: Modifier = Modifier
) {
    val lang = LocalAppLanguage.current
    val isRu = lang == AppLanguage.RU
    val activeCurrency by viewModel.activeCurrency.collectAsState()
    val sym = activeCurrency.symbol

    val planFact by viewModel.planFactResult.collectAsState()

    val planPrice by viewModel.planFactPlanPrice.collectAsState()
    val planVol by viewModel.planFactPlanVolume.collectAsState()
    val planCost by viewModel.planFactPlanCost.collectAsState()
    val planDays by viewModel.planFactPlanDays.collectAsState()

    val actPrice by viewModel.planFactActualPrice.collectAsState()
    val actVol by viewModel.planFactActualVolume.collectAsState()
    val actCost by viewModel.planFactActualCost.collectAsState()
    val actDays by viewModel.planFactActualDays.collectAsState()
    val actPenalties by viewModel.planFactActualPenalties.collectAsState()
    val capitalRate by viewModel.planFactCapitalRate.collectAsState()

    val statusColor = when (planFact.status) {
        PlanFactHealthStatus.OUTPERFORMING -> EmeraldPrimary
        PlanFactHealthStatus.ACCEPTABLE -> GoldAccent
        PlanFactHealthStatus.EROSION -> MaterialTheme.colorScheme.error
    }

    val statusIcon = when (planFact.status) {
        PlanFactHealthStatus.OUTPERFORMING -> Icons.Default.CheckCircle
        PlanFactHealthStatus.ACCEPTABLE -> Icons.Default.Warning
        PlanFactHealthStatus.EROSION -> Icons.Default.TrendingDown
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ExecutiveCard)
            .border(1.dp, ExecutiveBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("plan_fact_variance_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header: Title and Quick Import Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CompareArrows,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isRu) "ПЛАН-ФАКТ И МОСТ ОТКЛОНЕНИЙ" else "PLAN-FACT & VARIANCE BRIDGE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ElectricCyan,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (isRu) "Факторная декомпозиция чистой прибыли" else "Factorial profit bridge decomposition",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Import from Deal
                OutlinedButton(
                    onClick = { viewModel.importPlanFromCurrentDeal() },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp).testTag("btn_import_plan_deal")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(13.dp), tint = EmeraldPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRu) "План из сделки" else "From Deal",
                        fontSize = 11.sp,
                        color = EmeraldPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Status Banner & Analytical Verdict
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = statusColor.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRu) planFact.verdictRu else planFact.verdictEn,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            // Summary 3-KPI Dashboard Row (Revenue, Margin %, Net Profit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Revenue Card
                PlanFactMetricBox(
                    title = if (isRu) "Выручка" else "Revenue",
                    planStr = viewModel.formatCurrency(planFact.planRevenue),
                    actualStr = viewModel.formatCurrency(planFact.actualRevenue),
                    deltaStr = "${if (planFact.revenueDelta >= 0) "+" else ""}${viewModel.formatCurrency(planFact.revenueDelta)} (${if (planFact.revenueDeltaPct >= 0) "+" else ""}${planFact.revenueDeltaPct}%)",
                    isPositive = planFact.revenueDelta >= 0,
                    modifier = Modifier.weight(1f)
                )

                // Margin % Card
                PlanFactMetricBox(
                    title = if (isRu) "Маржа" else "Margin %",
                    planStr = "${planFact.planMarginPct}%",
                    actualStr = "${planFact.actualMarginPct}%",
                    deltaStr = "${if (planFact.marginPctDeltaPts >= 0) "+" else ""}${planFact.marginPctDeltaPts} п.п.",
                    isPositive = planFact.marginPctDeltaPts >= 0,
                    modifier = Modifier.weight(1f)
                )

                // Net Profit Card
                PlanFactMetricBox(
                    title = if (isRu) "Чистая прибыль" else "Net Profit",
                    planStr = viewModel.formatCurrency(planFact.planNetProfit),
                    actualStr = viewModel.formatCurrency(planFact.actualNetProfit),
                    deltaStr = "${if (planFact.netProfitDelta >= 0) "+" else ""}${viewModel.formatCurrency(planFact.netProfitDelta)}",
                    isPositive = planFact.netProfitDelta >= 0,
                    modifier = Modifier.weight(1.15f)
                )
            }

            HorizontalDivider(color = ExecutiveBorder, thickness = 1.dp)

            // Side-by-Side Input Table: Plan vs Fact
            Text(
                text = if (isRu) "ПАРАМЕТРЫ СРАВНЕНИЯ (ПЛАН VS ФАКТ)" else "COMPARISON INPUTS (PLAN VS ACTUAL)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            // Row 1: Price and Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialInputField(
                    value = planPrice,
                    onValueChange = {
                        viewModel.planFactPlanPrice.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "План цена" else "Plan Price",
                    prefix = sym,
                    modifier = Modifier.weight(1f),
                    testTag = "input_plan_price"
                )
                FinancialInputField(
                    value = actPrice,
                    onValueChange = {
                        viewModel.planFactActualPrice.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "Факт цена" else "Actual Price",
                    prefix = sym,
                    modifier = Modifier.weight(1f),
                    testTag = "input_act_price"
                )
            }

            // Row 2: Volume
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialInputField(
                    value = planVol,
                    onValueChange = {
                        viewModel.planFactPlanVolume.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "План объем (шт)" else "Plan Volume (pcs)",
                    modifier = Modifier.weight(1f),
                    testTag = "input_plan_vol"
                )
                FinancialInputField(
                    value = actVol,
                    onValueChange = {
                        viewModel.planFactActualVolume.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "Факт объем (шт)" else "Actual Volume (pcs)",
                    modifier = Modifier.weight(1f),
                    testTag = "input_act_vol"
                )
            }

            // Row 3: Cost (COGS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialInputField(
                    value = planCost,
                    onValueChange = {
                        viewModel.planFactPlanCost.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "План себестоимость" else "Plan COGS",
                    prefix = sym,
                    modifier = Modifier.weight(1f),
                    testTag = "input_plan_cost"
                )
                FinancialInputField(
                    value = actCost,
                    onValueChange = {
                        viewModel.planFactActualCost.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "Факт себестоимость" else "Actual COGS",
                    prefix = sym,
                    modifier = Modifier.weight(1f),
                    testTag = "input_act_cost"
                )
            }

            // Row 4: Deferral Days & Actual Penalties
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialInputField(
                    value = planDays,
                    onValueChange = {
                        viewModel.planFactPlanDays.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "План отсрочка (дн)" else "Plan Days",
                    modifier = Modifier.weight(1f),
                    testTag = "input_plan_days"
                )
                FinancialInputField(
                    value = actDays,
                    onValueChange = {
                        viewModel.planFactActualDays.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "Факт отсрочка (дн)" else "Actual Days",
                    modifier = Modifier.weight(1f),
                    testTag = "input_act_days"
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FinancialInputField(
                    value = actPenalties,
                    onValueChange = {
                        viewModel.planFactActualPenalties.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "Штрафы/Ретро-бонусы" else "Penalties / Claims",
                    prefix = sym,
                    modifier = Modifier.weight(1f),
                    testTag = "input_act_penalties"
                )
                FinancialInputField(
                    value = capitalRate,
                    onValueChange = {
                        viewModel.planFactCapitalRate.value = it
                        viewModel.updatePlanFactCalculations()
                    },
                    label = if (isRu) "Ставка капитала %" else "Capital Rate %",
                    suffix = "%",
                    modifier = Modifier.weight(1f),
                    testTag = "input_capital_rate"
                )
            }

            HorizontalDivider(color = ExecutiveBorder, thickness = 1.dp)

            // Факторный анализ прибыли (Variance Bridge Waterfall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isRu) "ФАКТОРНЫЙ МОСТ ОТКЛОНЕНИЙ ПРИБЫЛИ" else "PROFIT VARIANCE BRIDGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${if (planFact.netProfitDelta >= 0) "+" else ""}${viewModel.formatCurrency(planFact.netProfitDelta)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ExecutiveSurface)
                    .border(1.dp, ExecutiveBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Volume Factor
                BridgeFactorRow(
                    label = if (isRu) "1. Фактор объема партии (Volume Effect)" else "1. Volume Effect",
                    subtext = if (isRu) "ΔQ × (План Цена - План Себестоимость)" else "ΔQ × Unit Plan Margin",
                    amount = planFact.volumeEffectProfit,
                    currencySym = sym
                )

                // 2. Price Factor
                BridgeFactorRow(
                    label = if (isRu) "2. Ценовой фактор (Price Effect)" else "2. Price Effect",
                    subtext = if (isRu) "(Факт Цена - План Цена) × Факт Объем" else "ΔP × Actual Volume",
                    amount = planFact.priceEffectProfit,
                    currencySym = sym
                )

                // 3. Cost Factor
                BridgeFactorRow(
                    label = if (isRu) "3. Фактор себестоимости (COGS Effect)" else "3. Cost / COGS Effect",
                    subtext = if (isRu) "(План Себест. - Факт Себест.) × Факт Объем" else "ΔCost × Actual Volume",
                    amount = planFact.costEffectProfit,
                    currencySym = sym
                )

                // 4. Working Capital / Deferral Factor
                BridgeFactorRow(
                    label = if (isRu) "4. Фактор задержки оплаты (DSO Drag)" else "4. Payment Deferral (DSO Drag)",
                    subtext = if (isRu) "Стоимость отвлечения денег сверх плана" else "Working capital financing variance",
                    amount = planFact.dsoDeferralEffectProfit,
                    currencySym = sym
                )

                // 5. Penalties
                if (planFact.actualPenalties > 0.0) {
                    BridgeFactorRow(
                        label = if (isRu) "5. Штрафы, претензии и рекламации" else "5. Penalties & Commercial Claims",
                        subtext = if (isRu) "Прямые списания и компенсации" else "Direct contractual write-offs",
                        amount = planFact.penaltiesEffectProfit,
                        currencySym = sym
                    )
                }
            }

            // Action: Apply Fact to Main P&L
            Button(
                onClick = { viewModel.applyFactToPnl() },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_apply_fact_to_pnl")
            ) {
                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRu) "Перенести факт в основную модель P&L" else "Apply Actuals to P&L Model",
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PlanFactMetricBox(
    title: String,
    planStr: String,
    actualStr: String,
    deltaStr: String,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ExecutiveSurfaceVariant)
            .border(1.dp, ExecutiveBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = actualStr,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "План: $planStr",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = deltaStr,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPositive) EmeraldPrimary else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BridgeFactorRow(
    label: String,
    subtext: String,
    amount: Double,
    currencySym: String
) {
    val isPositive = amount >= 0.0
    val color = if (isPositive) EmeraldPrimary else MaterialTheme.colorScheme.error
    val sign = if (isPositive) "+" else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtext,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$sign$currencySym${String.format(java.util.Locale.US, "%,.2f", amount)}",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color,
            softWrap = false,
            maxLines = 1
        )
    }
}
