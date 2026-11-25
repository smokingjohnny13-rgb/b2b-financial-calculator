package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import com.example.data.ai.AiDealRiskAuditor
import com.example.data.ai.CommercialOcrScanner
import com.example.data.ai.OcrExtractedDealData
import com.example.data.model.AmortizationSummary
import com.example.data.model.DealApprovalStatus
import com.example.data.model.DealScoringResult
import com.example.data.model.LandedCostResult
import com.example.data.model.LtvMetrics
import com.example.data.model.MarginMarkupState
import com.example.data.model.PaymentFrequency
import com.example.data.model.PnlModel
import com.example.data.model.SavedDeal
import com.example.domain.calculator.CashGapResult
import com.example.domain.calculator.GiveGetResult
import com.example.domain.calculator.PaymentTermsResult
import com.example.domain.calculator.PlanFactResult
import com.example.domain.calculator.PriceWaterfallResult
import com.example.domain.calculator.PromoBreakEvenResult
import com.example.domain.calculator.TornadoAnalysisResult
import com.example.service.CsvExportService
import com.example.service.PdfExportService
import com.example.ui.components.AiAuditUiState
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delegate for AI Express Risk Audit, Commercial OCR Scanning,
 * and PDF / CSV document exports.
 */
class AiAndExportDelegate(
    private val scope: CoroutineScope,
    private val session: DealSessionState,
    private val aiRiskAuditor: AiDealRiskAuditor,
    private val commercialOcrScanner: CommercialOcrScanner
) {
    private val _aiAuditState = MutableStateFlow<AiAuditUiState>(AiAuditUiState.Idle)
    val aiAuditState: StateFlow<AiAuditUiState> = _aiAuditState.asStateFlow()

    val isOcrScanning = MutableStateFlow(false)
    val ocrScanResult = MutableStateFlow<OcrExtractedDealData?>(null)
    val ocrError = MutableStateFlow<String?>(null)
    val isOcrDialogOpen = MutableStateFlow(false)

    fun resetAiAuditState() {
        _aiAuditState.value = AiAuditUiState.Idle
    }

    fun runExpressAudit(
        margin: MarginMarkupState,
        waterfall: PriceWaterfallResult,
        terms: PaymentTermsResult,
        cashGap: CashGapResult,
        promo: PromoBreakEvenResult,
        forceLocal: Boolean = false
    ) {
        scope.launch {
            _aiAuditState.value = AiAuditUiState.Loading
            val isRu = session.appLanguage.value == AppLanguage.RU
            val result = aiRiskAuditor.auditDeal(
                dealTitle = session.dealTitle.value,
                clientName = session.clientName.value,
                currency = session.activeCurrency.value.code,
                costPrice = margin.cost,
                sellingPrice = margin.sellingPrice,
                grossMarginPct = margin.marginPct,
                netNetPrice = waterfall.netNetPrice,
                netNetMarginPct = waterfall.supplierMarginPct,
                paymentTermsDays = terms.paymentDays,
                financingCostPct = terms.annualInterestRatePct,
                cashGapDays = cashGap.cashConversionCycleDays,
                logisticsAndRebatesPct = waterfall.backBonusPct + waterfall.frontDiscountPct,
                breakEvenVolumeLiftPct = promo.breakEvenVolumeLiftPct,
                languageRu = isRu,
                forceLocal = forceLocal
            )
            result.fold(
                onSuccess = { audit -> _aiAuditState.value = AiAuditUiState.Success(audit) },
                onFailure = { error -> _aiAuditState.value = AiAuditUiState.Error(error.localizedMessage ?: "Audit failed") }
            )
        }
    }

    fun openOcrDialog() {
        ocrError.value = null
        isOcrDialogOpen.value = true
    }

    fun closeOcrDialog() {
        isOcrDialogOpen.value = false
    }

    fun scanDocumentBitmap(bitmap: Bitmap) {
        scope.launch {
            isOcrScanning.value = true
            ocrError.value = null
            ocrScanResult.value = null

            val result = commercialOcrScanner.scanDocumentImage(bitmap)
            result.fold(
                onSuccess = { data ->
                    isOcrScanning.value = false
                    ocrScanResult.value = data
                },
                onFailure = { error ->
                    isOcrScanning.value = false
                    val msg = error.localizedMessage ?: "OCR scanning failed"
                    ocrError.value = msg
                    session.emitLocalized("OCR error: $msg", "Ошибка OCR: $msg")
                }
            )
        }
    }

    fun exportPdf(
        context: Context,
        deal: SavedDeal,
        amortization: AmortizationSummary,
        pnl: PnlModel,
        ltv: LtvMetrics,
        margin: MarginMarkupState,
        landedCost: LandedCostResult,
        dealScoring: DealScoringResult,
        dealStatus: DealApprovalStatus,
        giveGet: GiveGetResult,
        tornado: TornadoAnalysisResult,
        waterfall: PriceWaterfallResult,
        cashGap: CashGapResult,
        planFact: PlanFactResult
    ) {
        scope.launch {
            val result = PdfExportService.generateAndShareDealPdf(
                context = context,
                deal = deal,
                amortization = amortization,
                pnl = pnl,
                ltv = ltv,
                margin = margin,
                landedCost = landedCost,
                dealScoring = dealScoring,
                dealStatus = dealStatus,
                giveGet = giveGet,
                tornado = tornado,
                waterfall = waterfall,
                cashGap = cashGap,
                planFact = planFact
            )
            result.onSuccess {
                session.emitMessage("Executive PDF proposal generated successfully!")
            }.onFailure { e ->
                session.emitMessage("Error generating PDF: ${e.localizedMessage}")
            }
        }
    }

    fun exportProductFlyerPdf(context: Context) {
        scope.launch {
            val result = PdfExportService.generateAndShareProductFlyerPdf(context)
            result.onSuccess {
                session.emitLocalized(
                    "LinkedIn Executive Product Flyer generated successfully!",
                    "Рекламная листовка для LinkedIn успешно создана!"
                )
            }.onFailure { e ->
                session.emitLocalized(
                    "Error generating flyer: ${e.localizedMessage}",
                    "Ошибка создания листовки: ${e.localizedMessage}"
                )
            }
        }
    }

    fun exportDealCsv(
        context: Context,
        deal: SavedDeal,
        scoring: DealScoringResult,
        cashGap: CashGapResult,
        pnl: PnlModel,
        amortization: AmortizationSummary,
        planFact: PlanFactResult
    ) {
        scope.launch {
            val result = CsvExportService.generateAndShareActiveDealCsv(
                context = context,
                deal = deal,
                scoring = scoring,
                cashGap = cashGap,
                pnl = pnl,
                amortization = amortization.schedule,
                planFact = planFact
            )
            result.onSuccess {
                session.emitLocalized(
                    "CSV spreadsheet exported successfully!",
                    "Финансовая модель CSV успешно экспортирована!"
                )
            }.onFailure { e ->
                session.emitLocalized(
                    "Error exporting CSV: ${e.localizedMessage}",
                    "Ошибка экспорта CSV: ${e.localizedMessage}"
                )
            }
        }
    }

    fun exportVaultRegistryCsv(context: Context, deals: List<SavedDeal>) {
        scope.launch {
            if (deals.isEmpty()) {
                session.emitLocalized("No saved deals to export.", "Нет сохраненных сделок для экспорта.")
                return@launch
            }
            val result = CsvExportService.generateAndShareVaultRegistryCsv(context, deals)
            result.onSuccess {
                session.emitLocalized(
                    "Deal registry exported (${deals.size} contracts)!",
                    "Реестр сделок экспортирован (${deals.size} договоров)!"
                )
            }.onFailure { e ->
                session.emitLocalized(
                    "Error exporting registry: ${e.localizedMessage}",
                    "Ошибка экспорта реестра: ${e.localizedMessage}"
                )
            }
        }
    }

    fun resetSessionState() {
        ocrScanResult.value = null
        ocrError.value = null
        isOcrDialogOpen.value = false
        _aiAuditState.value = AiAuditUiState.Idle
    }
}
