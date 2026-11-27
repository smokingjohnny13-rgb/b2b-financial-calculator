package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AmortizationScheme
import com.example.data.model.PaymentFrequency
import com.example.data.repository.FxCountryRegion
import com.example.data.repository.FxRateMode
import com.example.data.repository.LiveFxRatesService
import com.example.ui.components.CashGapTimelineCard
import com.example.ui.components.FinancialInputField
import com.example.ui.components.FxStressTestCard
import com.example.ui.components.HurdleRoicGuardrailCard
import com.example.ui.components.MetricCard
import com.example.ui.components.MultiCurrencyCreditCard
import com.example.ui.components.PaymentTermsCard
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.ExecutiveBorder
import com.example.ui.theme.ExecutiveCard
import com.example.ui.theme.ExecutiveSurface
import com.example.ui.theme.ExecutiveSurfaceVariant
import com.example.ui.theme.GoldAccent
import com.example.ui.viewmodel.BigDealViewModel
import com.example.util.toCleanDouble
import java.text.NumberFormat
import java.util.Locale

enum class FinancingSubTab {
    ALL,
    LOAN,
    FX_BASKET,
    WORKING_CAP,
    RISK_ROIC
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancingFxScreen(
    viewModel: BigDealViewModel,
    modifier: Modifier = Modifier
) {
    val activeCurrency by viewModel.activeCurrency.collectAsState()
    val currencySym = activeCurrency.symbol

    val principal by viewModel.principalAmount.collectAsState()
    val downPayment by viewModel.downPayment.collectAsState()
    val interestRate by viewModel.annualInterestRate.collectAsState()
    val tenureMonths by viewModel.tenureMonths.collectAsState()
    val frequency by viewModel.paymentFrequency.collectAsState()
    val amortization by viewModel.amortization.collectAsState()

    val isGracePeriodEnabled by viewModel.isGracePeriodEnabled.collectAsState()
    val graceTenureMonths by viewModel.graceTenureMonths.collectAsState()
    val graceInterestRate by viewModel.graceInterestRate.collectAsState()
    val postGraceInterestRate by viewModel.postGraceInterestRate.collectAsState()
    val customPaymentInput by viewModel.customMonthlyPayment.collectAsState()
    val selectedScheme by viewModel.amortizationScheme.collectAsState()

    val baseCurrency by viewModel.selectedCurrency.collectAsState()
    val targetCurrency by viewModel.targetCurrency.collectAsState()
    val convertedValue by viewModel.convertedDealValue.collectAsState()
    val crossRate by viewModel.currentCrossRate.collectAsState()
    val currencies by viewModel.currencyRates.collectAsState()
    val fxCountry by viewModel.fxCountryRegion.collectAsState()
    val isClientCountryEnabled by viewModel.isClientCountryEnabled.collectAsState()
    val fxRateMode by viewModel.fxRateMode.collectAsState()
    val fxPoints by viewModel.fxAvailablePoints.collectAsState()
    val fxSelectedPointId by viewModel.fxSelectedPointId.collectAsState()
    val isFxAutoBest by viewModel.isFxAutoBestEnabled.collectAsState()
    val isFxRefreshing by viewModel.isFxRatesRefreshing.collectAsState()
    val fxLastError by viewModel.fxLastError.collectAsState()
    val currentLang = LocalAppLanguage.current

    var showScheduleTable by remember { mutableStateOf(false) }
    var targetCurrencyDropdownExpanded by remember { mutableStateOf(false) }
    var showFxSourcesPanel by remember { mutableStateOf(false) }
    var activeSubTab by remember { mutableStateOf(FinancingSubTab.ALL) }

    val listState = rememberLazyListState()
    val numberFmt = remember { NumberFormat.getNumberInstance(Locale.US) }
    val fxAmountFmt = remember {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    LaunchedEffect(activeSubTab) {
        listState.scrollToItem(0)
    }

    val freqLocalizedName = when (frequency) {
        PaymentFrequency.WEEKLY -> tr("Weekly", "Еженедельный")
        PaymentFrequency.BIWEEKLY -> tr("Bi-Weekly", "Раз в 2 недели")
        PaymentFrequency.MONTHLY -> tr("Monthly", "Ежемесячный")
        PaymentFrequency.QUARTERLY -> tr("Quarterly", "Ежеквартальный")
        PaymentFrequency.ANNUALLY -> tr("Yearly", "Ежегодный")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("financing_fx_screen")
    ) {
        // Pinned Header & Sub-Navigation Tabs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tr("FINANCING & PURCHASE MODELER", "МОДЕЛИРОВАНИЕ ФИНАНСИРОВАНИЯ И СДЕЛОК"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tr(
                    "Structure enterprise client financing, credits, installments, and cross-currency parity.",
                    "Структурирование клиентского финансирования, кредитов, рассрочек и валютного паритета."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ScrollableTabRow(
                selectedTabIndex = activeSubTab.ordinal,
                containerColor = ExecutiveSurfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 4.dp,
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[activeSubTab.ordinal]),
                        color = EmeraldPrimary,
                        height = 2.5.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, ExecutiveBorder, RoundedCornerShape(12.dp))
            ) {
                FinancingSubTab.values().forEach { tab ->
                    val selected = activeSubTab == tab
                    val tabText = when (tab) {
                        FinancingSubTab.ALL -> tr("All Modules", "Все модули")
                        FinancingSubTab.LOAN -> tr("Loan & Amort.", "Кредит и график")
                        FinancingSubTab.FX_BASKET -> tr("FX & Basket", "Валюты и корзина")
                        FinancingSubTab.WORKING_CAP -> tr("Terms & Gap", "Отсрочка и разрыв")
                        FinancingSubTab.RISK_ROIC -> tr("Stress & ROIC", "Стресс и ROIC")
                    }
                    Tab(
                        selected = selected,
                        onClick = { activeSubTab = tab },
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) EmeraldPrimary.copy(alpha = 0.12f) else Color.Transparent),
                        text = {
                            Text(
                                text = tabText,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === SECTION A: LOAN & AMORTIZATION ===
            if (activeSubTab == FinancingSubTab.ALL || activeSubTab == FinancingSubTab.LOAN) {
                // 1. Primary Highlight Banner: Calculated Periodic Payment / Grace Period Structure
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(ExecutiveCard)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (amortization.hasGracePeriod) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = if (selectedScheme == AmortizationScheme.DIFFERENTIATED)
                                            "${freqLocalizedName.uppercase()} ${tr("DIFF. INSTALLMENT", "ДИФФЕРЕНЦ. ПЛАТЕЖ")}"
                                        else
                                            "${freqLocalizedName.uppercase()} ${tr("TWO-TIER INSTALLMENT", "ДВУХСТАВОЧНЫЙ ПЛАТЕЖ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(ElectricCyan.copy(alpha = 0.18f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${tr("Grace", "Грейс")}: ${amortization.graceInterestRatePct}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(GoldAccent.copy(alpha = 0.18f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${tr("Post", "После")}: ${amortization.postGraceInterestRatePct}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GoldAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${tr("IRR", "ПСК")}: ${amortization.trueEffectiveAprPct}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Grace Payment Card
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ExecutiveSurfaceVariant)
                                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = tr("IN GRACE", "В ГРЕЙС-ПЕРИОД"),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = viewModel.formatCurrency(amortization.gracePeriodicPayment),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Black,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${tr("Installments", "Выплаты")} 1–${amortization.gracePeriodsCount}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    // Post-Grace Payment Card
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ExecutiveSurfaceVariant)
                                            .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text(
                                                text = tr("POST-GRACE", "ПОСЛЕ ГРЕЙСА"),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = GoldAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (selectedScheme == AmortizationScheme.DIFFERENTIATED && amortization.firstPeriodicPayment != amortization.lastPeriodicPayment) {
                                                Text(
                                                    text = "${viewModel.formatCurrency(amortization.postGracePeriodicPayment)} → ${viewModel.formatCurrency(amortization.lastPeriodicPayment)}",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Black,
                                                    color = EmeraldPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            } else {
                                                Text(
                                                    text = viewModel.formatCurrency(amortization.postGracePeriodicPayment),
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Black,
                                                    color = EmeraldPrimary
                                                )
                                            }
                                            Text(
                                                text = "${tr("Installments", "Выплаты")} ${amortization.gracePeriodsCount + 1}–${amortization.schedule.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedScheme == AmortizationScheme.DIFFERENTIATED)
                                            "${freqLocalizedName.uppercase()} ${tr("DIFF. INSTALLMENT", "ДИФФЕРЕНЦ. ПЛАТЕЖ")}"
                                        else
                                            "${freqLocalizedName.uppercase()} ${tr("INSTALLMENT", "ПЛАТЕЖ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 0.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(EmeraldPrimary.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${interestRate}% APR",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldPrimary,
                                                fontWeight = FontWeight.Bold,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "${tr("IRR", "ПСК")}: ${amortization.trueEffectiveAprPct}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }

                                if (selectedScheme == AmortizationScheme.DIFFERENTIATED && amortization.firstPeriodicPayment != amortization.lastPeriodicPayment) {
                                    Column {
                                        Text(
                                            text = "${viewModel.formatCurrency(amortization.firstPeriodicPayment)} → ${viewModel.formatCurrency(amortization.lastPeriodicPayment)}",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = tr("Decreasing differentiated installment", "Дифференцированный убывающий платёж"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan,
                                            fontSize = 10.sp
                                        )
                                    }
                                } else {
                                    Text(
                                        text = viewModel.formatCurrency(amortization.periodicPayment),
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = ExecutiveBorder,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )

                            val principalVal = (principal.toCleanDouble() ?: 0.0).coerceAtLeast(0.0)
                            val downVal = (downPayment.toCleanDouble() ?: 0.0).coerceAtLeast(0.0)
                            val netLoanPrincipal = (principalVal - downVal).coerceAtLeast(0.0)
                            val overpaymentPct = if (netLoanPrincipal > 0.0) {
                                (amortization.totalInterest / netLoanPrincipal) * 100.0
                            } else 0.0
                            val pctPrefix = if (overpaymentPct > 0.0) "+" else ""

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tr("TOTAL PAYMENTS", "ИТОГО ВЫПЛАТ"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = viewModel.formatCurrency(amortization.totalPayments),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        softWrap = false,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (downVal > 0.0) {
                                            tr(
                                                "Total w/ DP: ${viewModel.formatCurrency(amortization.totalPayments + downVal)}",
                                                "Всего с ПВ: ${viewModel.formatCurrency(amortization.totalPayments + downVal)}"
                                            )
                                        } else {
                                            tr(
                                                "Principal: ${viewModel.formatCurrency(netLoanPrincipal)}",
                                                "Тело: ${viewModel.formatCurrency(netLoanPrincipal)}"
                                            )
                                        },
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(1.1f),
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = tr("OVERPAYMENT (INTEREST)", "ПЕРЕПЛАТА (ПРОЦЕНТЫ)"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "+${viewModel.formatCurrency(amortization.totalInterest)}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldAccent,
                                            softWrap = false,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (netLoanPrincipal > 0.0) {
                                            Text(
                                                text = " ($pctPrefix${String.format(Locale.US, "%.1f", overpaymentPct)}%)",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = GoldAccent.copy(alpha = 0.9f),
                                                softWrap = false,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (downVal > 0.0) {
                                        Text(
                                            text = tr(
                                                "Principal: ${viewModel.formatCurrency(netLoanPrincipal)}",
                                                "Тело: ${viewModel.formatCurrency(netLoanPrincipal)}"
                                            ),
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.End,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            if (amortization.isAccelerated) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(EmeraldPrimary.copy(alpha = 0.12f))
                                        .border(1.dp, EmeraldPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("card_accelerated_payoff")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Bolt,
                                                contentDescription = null,
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = tr("ACCELERATED PAYOFF", "ДОСРОЧНОЕ ПОГАШЕНИЕ"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                                Text(
                                                    text = tr(
                                                        "Paid off in ${amortization.acceleratedPeriods} periods (-${amortization.periodsSaved} periods)",
                                                        "Срок: ${amortization.acceleratedPeriods} выплат (быстрее на ${amortization.periodsSaved})"
                                                    ),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = tr("SAVED ON INTEREST", "ЭКОНОМИЯ НА %"),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = EmeraldPrimary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                                            )
                                            Text(
                                                text = "+${viewModel.formatCurrency(amortization.interestSaved)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = EmeraldPrimary,
                                                fontWeight = FontWeight.Black,
                                                softWrap = false,
                                                maxLines = 1,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Repayment Scheme & Frequency Selectors
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Repayment Scheme Selector (Annuity vs Differentiated)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr("REPAYMENT SCHEME", "СХЕМА ВЫПЛАТ ПО КРЕДИТУ"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedScheme == AmortizationScheme.ANNUITY)
                                        tr("Equal installments", "Аннуитет: равные платежи")
                                    else
                                        tr("Equal principal (less interest)", "Дифференц: равный долг (меньше переплата)"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan,
                                    fontSize = 10.sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.1f)
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ExecutiveSurfaceVariant)
                                    .border(1.dp, ExecutiveBorder, RoundedCornerShape(12.dp))
                                    .padding(3.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AmortizationScheme.values().forEach { schemeItem ->
                                    val isSelected = schemeItem == selectedScheme
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { viewModel.setAmortizationScheme(schemeItem) }
                                            .padding(vertical = 8.dp)
                                            .testTag("scheme_${schemeItem.name.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = tr(schemeItem.displayName, schemeItem.displayNameRu),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Payment Frequency
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = tr("PAYMENT FREQUENCY", "ПЕРИОДИЧНОСТЬ ПЛАТЕЖЕЙ"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                            ScrollableTabRow(
                                selectedTabIndex = PaymentFrequency.values().indexOf(frequency),
                                containerColor = ExecutiveSurfaceVariant,
                                contentColor = MaterialTheme.colorScheme.primary,
                                edgePadding = 0.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, ExecutiveBorder, RoundedCornerShape(12.dp))
                            ) {
                                PaymentFrequency.values().forEach { freq ->
                                    val selected = freq == frequency
                                    val freqText = when (freq) {
                                        PaymentFrequency.WEEKLY -> tr("Weekly", "Еженедельно")
                                        PaymentFrequency.BIWEEKLY -> tr("Bi-Weekly", "Раз в 2 нед.")
                                        PaymentFrequency.MONTHLY -> tr("Monthly", "Ежемесячно")
                                        PaymentFrequency.QUARTERLY -> tr("Quarterly", "Ежеквартально")
                                        PaymentFrequency.ANNUALLY -> tr("Yearly", "Ежегодно")
                                    }
                                    Tab(
                                        selected = selected,
                                        onClick = { viewModel.setPaymentFrequency(freq) },
                                        text = {
                                            Text(
                                                text = freqText,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 12.sp
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Financial Inputs (Principal, Down Payment, Interest Rate, Tenure)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ExecutiveSurface)
                            .border(1.dp, ExecutiveBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = tr("DEAL PRINCIPAL & TERMS", "СУММА СДЕЛКИ И УСЛОВИЯ"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FinancialInputField(
                                    label = tr("Deal Principal", "Сумма сделки / Кредит"),
                                    value = principal,
                                    onValueChange = {
                                        viewModel.principalAmount.value = it
                                        viewModel.updateFinancing()
                                    },
                                    prefix = currencySym,
                                    modifier = Modifier.weight(1f),
                                    testTag = "input_principal"
                                )
                                FinancialInputField(
                                    label = tr("Down Payment", "Первоначальный взнос"),
                                    value = downPayment,
                                    onValueChange = {
                                        viewModel.downPayment.value = it
                                        viewModel.updateFinancing()
                                    },
                                    prefix = currencySym,
                                    modifier = Modifier.weight(1f),
                                    testTag = "input_downpayment"
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FinancialInputField(
                                    label = tr("Annual APR (%)", "Годовая ставка (%)"),
                                    value = interestRate,
                                    onValueChange = {
                                        viewModel.annualInterestRate.value = it
                                        viewModel.updateFinancing()
                                    },
                                    suffix = "%",
                                    modifier = Modifier.weight(1f),
                                    testTag = "input_apr"
                                )
                                FinancialInputField(
                                    label = tr("Tenure", "Срок"),
                                    value = tenureMonths,
                                    onValueChange = {
                                        viewModel.tenureMonths.value = it
                                        viewModel.updateFinancing()
                                    },
                                    suffix = tr("mo", "мес"),
                                    modifier = Modifier.weight(1f),
                                    testTag = "input_tenure"
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                FinancialInputField(
                                    label = tr("Custom Monthly Payment (Early Payoff)", "Свой ежемесячный платеж (досрочно)"),
                                    value = customPaymentInput,
                                    onValueChange = { viewModel.setCustomMonthlyPayment(it) },
                                    prefix = currencySym,
                                    modifier = Modifier.weight(1f),
                                    formulaId = "prepayment",
                                    testTag = "input_custom_payment"
                                )
                                if (customPaymentInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.clearCustomMonthlyPayment() },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3b. Grace Period & Two-Tier Financing Configuration Card
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ExecutiveSurface)
                            .border(
                                1.dp,
                                if (isGracePeriodEnabled) ElectricCyan.copy(alpha = 0.5f) else ExecutiveBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("GRACE PERIOD & TWO-TIER APR", "ЛЬГОТНЫЙ ГРЕЙС-ПЕРИОД И ДВУХСТАВОЧНЫЙ %"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isGracePeriodEnabled) ElectricCyan else MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = tr(
                                            "Reduced/subsidized rate during grace + standard APR post-grace",
                                            "Льготная ставка в грейс + стандартная ставка после окончания"
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }

                                Switch(
                                    checked = isGracePeriodEnabled,
                                    onCheckedChange = { viewModel.toggleGracePeriod(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = EmeraldPrimary,
                                        checkedTrackColor = EmeraldPrimary.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.testTag("switch_grace_period")
                                )
                            }

                            if (isGracePeriodEnabled) {
                                HorizontalDivider(color = ExecutiveBorder, thickness = 1.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FinancialInputField(
                                        label = tr("Grace Duration", "Срок грейса"),
                                        value = graceTenureMonths,
                                        onValueChange = {
                                            viewModel.graceTenureMonths.value = it
                                            viewModel.updateFinancing()
                                        },
                                        suffix = tr("mo", "мес"),
                                        formulaId = "grace_period",
                                        modifier = Modifier.weight(1f),
                                        testTag = "input_grace_tenure"
                                    )
                                    FinancialInputField(
                                        label = tr("Grace Rate (%)", "Ставка в грейс (%)"),
                                        value = graceInterestRate,
                                        onValueChange = {
                                            viewModel.graceInterestRate.value = it
                                            viewModel.updateFinancing()
                                        },
                                        suffix = "%",
                                        formulaId = "grace_period",
                                        modifier = Modifier.weight(1f),
                                        testTag = "input_grace_rate"
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FinancialInputField(
                                        label = tr("Post-Grace Rate (%)", "Ставка после грейса (%)"),
                                        value = postGraceInterestRate,
                                        onValueChange = {
                                            viewModel.postGraceInterestRate.value = it
                                            viewModel.updateFinancing()
                                        },
                                        suffix = "%",
                                        formulaId = "grace_period",
                                        modifier = Modifier.weight(1f),
                                        testTag = "input_post_grace_rate"
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FinancialInputField(
                                        label = tr("Custom Monthly Payment (During/Post Grace)", "Свой ежемесячный платеж (досрочно)"),
                                        value = customPaymentInput,
                                        onValueChange = { viewModel.setCustomMonthlyPayment(it) },
                                        prefix = currencySym,
                                        formulaId = "prepayment",
                                        modifier = Modifier.weight(1f),
                                        testTag = "input_grace_custom_payment"
                                    )
                                    if (customPaymentInput.isNotEmpty()) {
                                        IconButton(
                                            onClick = { viewModel.clearCustomMonthlyPayment() },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                // Quick Grace Presets
                                Text(
                                    text = tr("QUICK GRACE PRESETS", "БЫСТРЫЕ ШАБЛОНЫ ГРЕЙСА:"),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    AssistChip(
                                        onClick = {
                                            viewModel.graceTenureMonths.value = "3"
                                            viewModel.graceInterestRate.value = "0.0"
                                            viewModel.updateFinancing()
                                        },
                                        label = { Text("0% (3 " + tr("mo", "мес") + ")", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    AssistChip(
                                        onClick = {
                                            viewModel.graceTenureMonths.value = "6"
                                            viewModel.graceInterestRate.value = "0.0"
                                            viewModel.updateFinancing()
                                        },
                                        label = { Text("0% (6 " + tr("mo", "мес") + ")", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    AssistChip(
                                        onClick = {
                                            viewModel.graceTenureMonths.value = "6"
                                            viewModel.graceInterestRate.value = "3.0"
                                            viewModel.updateFinancing()
                                        },
                                        label = { Text("3% (6 " + tr("mo", "мес") + ")", fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable Amortization Schedule Preview
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ExecutiveSurface)
                            .border(1.dp, ExecutiveBorder, RoundedCornerShape(14.dp))
                            .clickable { showScheduleTable = !showScheduleTable }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Payments,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${tr("Amortization Breakdown", "График платежей")} (${amortization.schedule.size} ${tr("installments", "платежей")})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Icon(
                                imageVector = if (showScheduleTable) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Toggle schedule",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (showScheduleTable) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(ExecutiveCard)
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("#", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(42.dp))
                                    Text(tr("Payment", "Платеж"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(tr("Principal", "Осн. долг"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(tr("Interest", "Проценты"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(tr("Balance", "Остаток"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
                                }

                                HorizontalDivider(color = ExecutiveBorder, thickness = 1.dp)

                                amortization.schedule.take(16).forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (row.isGracePeriod) ElectricCyan.copy(alpha = 0.08f)
                                                else Color.Transparent
                                            )
                                            .padding(vertical = 2.dp, horizontal = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            modifier = Modifier.width(42.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${row.period}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (row.isGracePeriod) {
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(3.dp))
                                                        .background(ElectricCyan.copy(alpha = 0.25f))
                                                        .padding(horizontal = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "G",
                                                        fontSize = 8.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = ElectricCyan
                                                    )
                                                }
                                            }
                                        }
                                        Text(viewModel.formatCurrency(row.payment), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (row.isGracePeriod) ElectricCyan else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                        Text(viewModel.formatCurrency(row.principal), style = MaterialTheme.typography.bodySmall, color = EmeraldPrimary, modifier = Modifier.weight(1f))
                                        Text(viewModel.formatCurrency(row.interest), style = MaterialTheme.typography.bodySmall, color = GoldAccent, modifier = Modifier.weight(1f))
                                        Text(viewModel.formatCurrency(row.remainingBalance), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.2f))
                                    }
                                }

                                if (amortization.schedule.size > 16) {
                                    Text(
                                        text = "+ ${amortization.schedule.size - 16} ${tr("additional installments included in exportable PDF", "следующих платежей включены в PDF-отчет")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // === SECTION B: CURRENCIES & CREDIT BASKET ===
            if (activeSubTab == FinancingSubTab.ALL || activeSubTab == FinancingSubTab.FX_BASKET) {
                // Currency Cross-Exchange Engine
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ExecutiveSurface)
                            .border(1.dp, ExecutiveBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        val activeFxPoint = fxPoints.firstOrNull { it.id == fxSelectedPointId }
                        val isRu = currentLang == AppLanguage.RU
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CurrencyExchange,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tr("CURRENCY CONVERTER", "ВАЛЮТНЫЙ КОНВЕРТЕР"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    onClick = {
                                        showFxSourcesPanel = true
                                        viewModel.refreshLiveFxRates()
                                    },
                                    enabled = !isFxRefreshing,
                                    shape = RoundedCornerShape(8.dp),
                                    color = EmeraldPrimary.copy(alpha = 0.14f),
                                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.6f)),
                                    modifier = Modifier.testTag("btn_refresh_fx_rates")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isFxRefreshing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 2.dp,
                                                color = EmeraldPrimary
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = tr("Update FX rates", "Обновить курсы"),
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isFxRefreshing) tr("Syncing...", "Обновление...") else tr("Update Rates", "Обновить курсы"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldPrimary,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { showFxSourcesPanel = !showFxSourcesPanel },
                                    shape = RoundedCornerShape(8.dp),
                                    color = ExecutiveCard,
                                    border = BorderStroke(1.dp, ExecutiveBorder),
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .testTag("btn_toggle_fx_sources")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Public,
                                            contentDescription = null,
                                            tint = if (activeFxPoint != null) EmeraldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        val pointLabel = when {
                                            activeFxPoint != null -> {
                                                val modePrefix = if (isFxAutoBest) tr("Auto", "Авто") else tr("Manual", "Точка")
                                                val name = if (isRu) activeFxPoint.providerNameRu else activeFxPoint.providerNameEn
                                                "$modePrefix · $name"
                                            }
                                            isClientCountryEnabled -> {
                                                val regionLabel = if (isRu) fxCountry.shortRu else fxCountry.shortEn
                                                "${tr("Country", "Страна")}: $regionLabel"
                                            }
                                            else -> tr("Global Market Spot", "Рынок (без привязки к стране)")
                                        }
                                        Text(
                                            text = pointLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = if (showFxSourcesPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Box {
                                    Surface(
                                        onClick = { targetCurrencyDropdownExpanded = true },
                                        shape = RoundedCornerShape(8.dp),
                                        color = ExecutiveSurfaceVariant,
                                        border = BorderStroke(1.dp, ExecutiveBorder),
                                        modifier = Modifier.testTag("currency_select_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "$baseCurrency → $targetCurrency",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = tr("Select target currency", "Выбрать целевую валюту"),
                                                tint = ElectricCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = targetCurrencyDropdownExpanded,
                                        onDismissRequest = { targetCurrencyDropdownExpanded = false }
                                    ) {
                                        currencies.forEach { curr ->
                                            DropdownMenuItem(
                                                text = { Text("${curr.code} - ${curr.name} (${curr.symbol})") },
                                                onClick = {
                                                    viewModel.setTargetCurrency(curr.code)
                                                    targetCurrencyDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1.1f)) {
                                    Text(
                                        text = tr("CONVERTED CONTRACT VALUE", "СУММА В ЦЕЛЕВОЙ ВАЛЮТЕ"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$targetCurrency ${fxAmountFmt.format(convertedValue)}",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        softWrap = false,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(
                                    modifier = Modifier.weight(0.9f),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = tr("EXCHANGE RATE", "КУРС КОНВЕРТАЦИИ"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.End
                                    )
                                    Text(
                                        text = "1 $baseCurrency = $crossRate $targetCurrency",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.End,
                                        softWrap = false,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            val cbPoint = if (isClientCountryEnabled) {
                                LiveFxRatesService.findCentralBankPoint(fxPoints, fxCountry)
                            } else {
                                null
                            }
                            val cbCrossRate = cbPoint?.crossRate(baseCurrency, targetCurrency)
                            if (cbPoint != null && cbCrossRate != null && cbCrossRate > 0.0) {
                                val cbLabel = if (isRu) cbPoint.providerNameRu else cbPoint.providerNameEn
                                val spreadPct = ((crossRate - cbCrossRate) / cbCrossRate) * 100.0
                                val spreadSign = if (spreadPct >= 0.0) "+" else ""
                                val spreadText = String.format(Locale.US, "%s%.2f%%", spreadSign, spreadPct)
                                val cbFormatted = String.format(Locale.US, "%.4f", cbCrossRate)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ExecutiveCard)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$cbLabel: 1 $baseCurrency = $cbFormatted $targetCurrency",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${tr("vs CB", "к НБ")}: $spreadText",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (kotlin.math.abs(spreadPct) < 0.05) EmeraldPrimary else GoldAccent,
                                        fontSize = 10.sp,
                                        softWrap = false
                                    )
                                }
                            }

                            // Compact Switch: Client Country & Central Bank Reference
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ExecutiveSurfaceVariant)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr("Client country (Central Bank & Bank)", "Страна клиента (Нацбанк / Банк)"),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isClientCountryEnabled) {
                                            val regionLabel = if (isRu) fxCountry.shortRu else fxCountry.shortEn
                                            "${tr("Country", "Страна")}: $regionLabel"
                                        } else {
                                            tr("Off — global market rates only", "Выкл — только мировой рынок")
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                                Switch(
                                    checked = isClientCountryEnabled,
                                    onCheckedChange = { enabled ->
                                        if (enabled) showFxSourcesPanel = true
                                        viewModel.setClientCountryEnabled(enabled)
                                    },
                                    modifier = Modifier.testTag("switch_client_country"),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = EmeraldPrimary
                                    )
                                )
                            }

                            AnimatedVisibility(visible = showFxSourcesPanel) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HorizontalDivider(color = ExecutiveBorder, thickness = 0.5.dp)

                                    if (isClientCountryEnabled) {
                                        Text(
                                            text = tr("SELECT CLIENT COUNTRY", "ВЫБЕРИТЕ СТРАНУ КЛИЕНТА"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ElectricCyan,
                                            fontWeight = FontWeight.Bold
                                        )

                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(FxCountryRegion.entries) { region ->
                                                val isRegionSelected = region == fxCountry
                                                FilterChip(
                                                    selected = isRegionSelected,
                                                    onClick = { viewModel.setFxCountryRegion(region) },
                                                    label = {
                                                        Text(
                                                            text = if (isRu) region.shortRu else region.shortEn,
                                                            fontSize = 11.sp
                                                        )
                                                    },
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        selectedContainerColor = ElectricCyan,
                                                        selectedLabelColor = Color.Black,
                                                        containerColor = ExecutiveSurfaceVariant,
                                                        labelColor = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    border = FilterChipDefaults.filterChipBorder(
                                                        enabled = true,
                                                        selected = isRegionSelected,
                                                        borderColor = ExecutiveBorder
                                                    )
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val isMarketBank = fxRateMode == FxRateMode.MARKET_BANK
                                            FilterChip(
                                                selected = isMarketBank,
                                                onClick = { viewModel.setFxRateMode(FxRateMode.MARKET_BANK) },
                                                label = {
                                                    Text(
                                                        text = tr("Market / Bank", "Рынок / Банк"),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = EmeraldPrimary,
                                                    selectedLabelColor = Color.Black,
                                                    containerColor = ExecutiveSurfaceVariant,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = isMarketBank,
                                                    borderColor = ExecutiveBorder
                                                )
                                            )
                                            FilterChip(
                                                selected = !isMarketBank,
                                                onClick = { viewModel.setFxRateMode(FxRateMode.CENTRAL_BANK) },
                                                label = {
                                                    Text(
                                                        text = tr("Central Bank", "Нацбанк"),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                },
                                                modifier = Modifier.weight(1f),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = EmeraldPrimary,
                                                    selectedLabelColor = Color.Black,
                                                    containerColor = ExecutiveSurfaceVariant,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                border = FilterChipDefaults.filterChipBorder(
                                                    enabled = true,
                                                    selected = !isMarketBank,
                                                    borderColor = ExecutiveBorder
                                                )
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(ExecutiveSurfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = tr("Auto-pick best point", "Автовыбор лучшей точки"),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isFxAutoBest) {
                                                    tr("Picks highest conversion rate automatically", "Автоматически берёт максимальный курс")
                                                } else {
                                                    tr("Manual point selection enabled", "Включён ручной выбор точки")
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Switch(
                                            checked = isFxAutoBest,
                                            onCheckedChange = { viewModel.setFxAutoBestEnabled(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.Black,
                                                checkedTrackColor = EmeraldPrimary
                                            )
                                        )
                                    }

                                    if (fxLastError != null) {
                                        Text(
                                            text = fxLastError ?: "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GoldAccent
                                        )
                                    }

                                    if (fxPoints.isEmpty() && !isFxRefreshing) {
                                        Text(
                                            text = tr(
                                                "Tap \"Update Rates\" to search live rates from central banks and market feeds.",
                                                "Нажмите «Обновить курсы» для загрузки онлайн-курсов нацбанков, банков и рынка."
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            fxPoints.take(6).forEachIndexed { idx, point ->
                                                val isActive = point.id == fxSelectedPointId
                                                val quote = point.crossRate(baseCurrency, targetCurrency)
                                                val quoteText = if (quote != null) {
                                                    String.format(Locale.US, "%.4f", quote)
                                                } else {
                                                    "—"
                                                }
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isActive) EmeraldPrimary.copy(alpha = 0.14f) else ExecutiveCard
                                                        )
                                                        .border(
                                                            1.dp,
                                                            if (isActive) EmeraldPrimary else ExecutiveBorder,
                                                            RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable { viewModel.selectManualFxPoint(point.id) }
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            if (idx == 0) {
                                                                Text(
                                                                    text = tr("BEST ", "ЛУЧШИЙ "),
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = EmeraldPrimary
                                                                )
                                                            }
                                                            Text(
                                                                text = if (isRu) point.providerNameRu else point.providerNameEn,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        Text(
                                                            text = if (isRu) point.pointDetailsRu else point.pointDetailsEn,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "$quoteText $targetCurrency",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isActive) EmeraldPrimary else ElectricCyan,
                                                            softWrap = false
                                                        )
                                                        if (isActive) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = EmeraldPrimary,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Multi-Currency Credit Money & Hedging (up to 4 currencies)
                item {
                    MultiCurrencyCreditCard(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // === SECTION C: WORKING CAPITAL & TERMS ===
            if (activeSubTab == FinancingSubTab.ALL || activeSubTab == FinancingSubTab.WORKING_CAP) {
                // Payment Terms & Working Capital Cost
                item {
                    PaymentTermsCard(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Cash Gap & Working Capital Peak (CCC & Overdraft Bridge)
                item {
                    CashGapTimelineCard(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // === SECTION D: RISK & ROIC GUARDRAIL ===
            if (activeSubTab == FinancingSubTab.ALL || activeSubTab == FinancingSubTab.RISK_ROIC) {
                // FX Stress-Test & Margin Wipeout Threshold
                item {
                    FxStressTestCard(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Hurdle Rate & ROIC Guardrail (Shareholder Value Creation)
                item {
                    HurdleRoicGuardrailCard(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
