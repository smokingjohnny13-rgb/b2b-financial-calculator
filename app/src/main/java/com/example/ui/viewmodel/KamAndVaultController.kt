package com.example.ui.viewmodel

import com.example.data.model.ClientNegotiationProfile
import com.example.data.model.DealApprovalStatus
import com.example.data.model.DealScoringResult
import com.example.data.model.NegotiationTouchpoint
import com.example.data.model.PaymentFrequency
import com.example.data.model.SavedDeal
import com.example.data.repository.ClientNegotiationRepository
import com.example.data.repository.DealRepository
import com.example.domain.calculator.BasketSkuItem
import com.example.domain.calculator.BlendedBasketResult
import com.example.domain.calculator.CashGapResult
import com.example.domain.calculator.CommercialAnalystEngine
import com.example.domain.calculator.DealScoringEngine
import com.example.domain.calculator.FxStressTestResult
import com.example.domain.calculator.GiveConcessionType
import com.example.domain.calculator.GiveGetEngine
import com.example.domain.calculator.GiveGetResult
import com.example.domain.calculator.KamCorridorResult
import com.example.domain.calculator.KamNegotiationEngine
import com.example.domain.calculator.KamOnePagerSummary
import com.example.domain.calculator.KamScenarioType
import com.example.domain.calculator.PlanFactResult
import com.example.domain.calculator.PlanFactVarianceEngine
import com.example.domain.calculator.RebateTierSimulationResult
import com.example.domain.calculator.RetailSlaPenaltyResult
import com.example.domain.calculator.TradeOffArbitrageResult
import com.example.ui.i18n.AppLanguage
import com.example.util.toCleanDouble
import com.example.util.toCleanInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Controller for Deal Scoring, Approval Desk, KAM Negotiation Corridor/SLA,
 * Give-Get Balancing, Commercial Analyst tools, Plan-Fact Variance, Clients, and Deal Vault.
 */
class KamAndVaultController(
    private val scope: CoroutineScope,
    private val session: DealSessionState,
    private val dealRepository: DealRepository,
    private val clientRepository: ClientNegotiationRepository,
    private val fxStressProvider: () -> FxStressTestResult,
    private val cashGapProvider: () -> CashGapResult,
    private val hurdleRateProvider: () -> Double
) {
    val savedDeals: StateFlow<List<SavedDeal>> = dealRepository.deals
    val isSyncing: StateFlow<Boolean> = dealRepository.isSyncing

    // Plan-Fact Variance Bridge
    val planFactPlanPrice = MutableStateFlow("100.0")
    val planFactPlanVolume = MutableStateFlow("1000")
    val planFactPlanCost = MutableStateFlow("60.0")
    val planFactPlanDays = MutableStateFlow("30")
    val planFactActualPrice = MutableStateFlow("96.0")
    val planFactActualVolume = MutableStateFlow("1050")
    val planFactActualCost = MutableStateFlow("63.0")
    val planFactActualDays = MutableStateFlow("48")
    val planFactActualPenalties = MutableStateFlow("500.0")
    val planFactCapitalRate = MutableStateFlow("18.0")

    private val _planFactResult = MutableStateFlow(
        PlanFactVarianceEngine.calculate(
            planPrice = 100.0,
            planVolume = 1000,
            planCost = 60.0,
            planDays = 30,
            actualPrice = 96.0,
            actualVolume = 1050,
            actualCost = 63.0,
            actualDays = 48,
            actualPenalties = 500.0,
            capitalRatePct = 18.0
        )
    )
    val planFactResult: StateFlow<PlanFactResult> = _planFactResult.asStateFlow()

    // KAM Corridor, SLA & One-Pager
    val kamBaseShelfPrice = MutableStateFlow("100")
    val kamBaseCogs = MutableStateFlow("60")
    val kamActiveScenarioType = MutableStateFlow(KamScenarioType.TARGET)
    private val _kamCorridorResult = MutableStateFlow(
        KamNegotiationEngine.calculateCorridor(100.0, 60.0, KamScenarioType.TARGET)
    )
    val kamCorridorResult: StateFlow<KamCorridorResult> = _kamCorridorResult.asStateFlow()

    val kamDealAmount = MutableStateFlow("100000")
    val kamFillRatePct = MutableStateFlow("95")
    val kamPenaltyRatePct = MutableStateFlow("12")
    val kamTimeSlotPenalty = MutableStateFlow("1500")
    private val _kamSlaResult = MutableStateFlow(
        KamNegotiationEngine.calculateRetailSlaPenalty(100000.0, 95.0, 12.0, 1500.0, 30.0)
    )
    val kamSlaResult: StateFlow<RetailSlaPenaltyResult> = _kamSlaResult.asStateFlow()

    private val _kamOnePagerSummary = MutableStateFlow(
        KamNegotiationEngine.buildOnePagerSummary(_kamCorridorResult.value, _kamSlaResult.value)
    )
    val kamOnePagerSummary: StateFlow<KamOnePagerSummary> = _kamOnePagerSummary.asStateFlow()
    val showKamOnePagerDialog = MutableStateFlow(false)

    // Give-Get Engine
    val giveConcessionType = MutableStateFlow(GiveConcessionType.PAYMENT_DEFERRAL)
    val giveConcessionValue = MutableStateFlow("30")
    private val _giveGetResult = MutableStateFlow(
        GiveGetEngine.calculate(
            sellingPrice = 100.0,
            cogs = 60.0,
            baseVolume = 1000,
            currentDiscountPct = 5.0,
            currentBonusPct = 5.0,
            currentPaymentDays = 30,
            waccRatePct = 18.0,
            concessionType = GiveConcessionType.PAYMENT_DEFERRAL,
            concessionValue = 30.0
        )
    )
    val giveGetResult: StateFlow<GiveGetResult> = _giveGetResult.asStateFlow()

    // Commercial Analyst
    val tradeOffDealAmount = MutableStateFlow("100000")
    val tradeOffDiscountPct = MutableStateFlow("3")
    val tradeOffDeferralDays = MutableStateFlow("30")
    val tradeOffAnnualRate = MutableStateFlow("20")
    private val _tradeOffResult = MutableStateFlow(
        CommercialAnalystEngine.calculateTradeOff(100000.0, 3.0, 30, 20.0)
    )
    val tradeOffResult: StateFlow<TradeOffArbitrageResult> = _tradeOffResult.asStateFlow()

    private val _basketItems = MutableStateFlow(CommercialAnalystEngine.DEFAULT_BASKET_ITEMS)
    val basketItems: StateFlow<List<BasketSkuItem>> = _basketItems.asStateFlow()
    private val _blendedBasketResult = MutableStateFlow(
        CommercialAnalystEngine.calculateBlendedBasket(CommercialAnalystEngine.DEFAULT_BASKET_ITEMS)
    )
    val blendedBasketResult: StateFlow<BlendedBasketResult> = _blendedBasketResult.asStateFlow()

    val rebateActualVolume = MutableStateFlow("120000")
    val rebateBaseMarginPct = MutableStateFlow("30")
    private val _rebateSimulationResult = MutableStateFlow(
        CommercialAnalystEngine.simulateRebateTiers(120000.0, 30.0)
    )
    val rebateSimulationResult: StateFlow<RebateTierSimulationResult> = _rebateSimulationResult.asStateFlow()

    // Deal Scoring & Clients
    val clientsList: StateFlow<List<ClientNegotiationProfile>> = clientRepository.clients
    val selectedClientId = MutableStateFlow<String?>("client-omnitech-01")
    val selectedClient: StateFlow<ClientNegotiationProfile?> = combine(clientsList, selectedClientId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val dealApprovalSellingPrice = MutableStateFlow("100.0")
    val dealApprovalCostPrice = MutableStateFlow("55.0")
    val dealApprovalVolumeUnits = MutableStateFlow("1500")
    val dealApprovalDiscountPct = MutableStateFlow("5.0")
    val dealApprovalBonusPct = MutableStateFlow("8.0")
    val dealApprovalPaymentDays = MutableStateFlow("30")
    val dealApprovalFinancingRate = MutableStateFlow("18.0")
    val dealApprovalStatus = MutableStateFlow(DealApprovalStatus.DRAFT)

    private val _dealScoringResult = MutableStateFlow(
        DealScoringEngine.calculate(
            sellingPrice = 100.0,
            costPrice = 55.0,
            volumeUnits = 1500,
            onInvoiceDiscountPct = 5.0,
            offInvoiceBonusPct = 8.0,
            paymentTermsDays = 30,
            financingAnnualRatePct = 18.0,
            languageRu = false
        )
    )
    val dealScoringResult: StateFlow<DealScoringResult> = _dealScoringResult.asStateFlow()

    fun updatePlanFactCalculations() {
        _planFactResult.value = PlanFactVarianceEngine.calculate(
            planPrice = planFactPlanPrice.value.toCleanDouble() ?: 100.0,
            planVolume = planFactPlanVolume.value.toCleanInt() ?: 1000,
            planCost = planFactPlanCost.value.toCleanDouble() ?: 60.0,
            planDays = planFactPlanDays.value.toCleanInt() ?: 30,
            actualPrice = planFactActualPrice.value.toCleanDouble() ?: 96.0,
            actualVolume = planFactActualVolume.value.toCleanInt() ?: 1050,
            actualCost = planFactActualCost.value.toCleanDouble() ?: 63.0,
            actualDays = planFactActualDays.value.toCleanInt() ?: 48,
            actualPenalties = planFactActualPenalties.value.toCleanDouble() ?: 500.0,
            capitalRatePct = planFactCapitalRate.value.toCleanDouble() ?: 18.0
        )
    }

    fun attachPlanFact(deal: SavedDeal): SavedDeal = deal.copy(
        hasPlanFactData = true,
        planFactPlanPrice = planFactPlanPrice.value.toCleanDouble() ?: 100.0,
        planFactPlanVolume = planFactPlanVolume.value.toCleanInt() ?: 1000,
        planFactPlanCost = planFactPlanCost.value.toCleanDouble() ?: 60.0,
        planFactPlanDays = planFactPlanDays.value.toCleanInt() ?: 30,
        planFactActualPrice = planFactActualPrice.value.toCleanDouble() ?: 96.0,
        planFactActualVolume = planFactActualVolume.value.toCleanInt() ?: 1050,
        planFactActualCost = planFactActualCost.value.toCleanDouble() ?: 63.0,
        planFactActualDays = planFactActualDays.value.toCleanInt() ?: 48,
        planFactActualPenalties = planFactActualPenalties.value.toCleanDouble() ?: 0.0,
        planFactCapitalRate = planFactCapitalRate.value.toCleanDouble() ?: 18.0
    )

    fun saveDealScenario(
        principal: Double,
        interestRatePct: Double,
        tenureMonths: Int,
        frequency: PaymentFrequency,
        periodicPayment: Double,
        ltv: Double,
        ltvCacRatio: Double,
        marginPct: Double,
        markupPct: Double,
        roiPct: Double,
        paybackMonths: Double
    ) {
        scope.launch {
            val deal = attachPlanFact(
                SavedDeal(
                    title = session.dealTitle.value,
                    clientName = session.clientName.value,
                    currencyCode = session.selectedCurrency.value,
                    dealValue = principal,
                    interestRatePct = interestRatePct,
                    tenureMonths = tenureMonths,
                    frequency = frequency,
                    periodicPayment = periodicPayment,
                    ltv = ltv,
                    ltvCacRatio = ltvCacRatio,
                    marginPct = marginPct,
                    markupPct = markupPct,
                    roiPct = roiPct,
                    paybackMonths = paybackMonths,
                    updatedAt = System.currentTimeMillis()
                )
            )
            dealRepository.saveDeal(deal)
                .onSuccess {
                    session.emitLocalized(
                        "Deal proposal saved & synced: ${deal.title}",
                        "Сценарий сделки сохранен и синхронизирован: ${deal.title}"
                    )
                }
                .onFailure {
                    session.emitLocalized(
                        "Saved locally, but cloud sync failed. Check permissions or connection.",
                        "Сохранено локально, но облачная синхронизация не удалась."
                    )
                }
        }
    }

    fun deleteDeal(dealId: String) {
        scope.launch {
            dealRepository.deleteDeal(dealId)
                .onSuccess {
                    session.emitLocalized("Deal scenario deleted", "Сценарий сделки удален")
                }
                .onFailure {
                    session.emitLocalized(
                        "Removed locally, but cloud deletion failed.",
                        "Удалено локально, но удаление в облаке не удалось."
                    )
                }
        }
    }

    fun updateKamCorridor() {
        _kamCorridorResult.value = KamNegotiationEngine.calculateCorridor(
            baseShelfPrice = kamBaseShelfPrice.value.toCleanDouble() ?: 100.0,
            baseCogs = kamBaseCogs.value.toCleanDouble() ?: 60.0,
            activeType = kamActiveScenarioType.value
        )
        updateKamOnePager()
        updateGiveGetCalculation()
    }

    fun selectKamScenario(type: KamScenarioType) {
        kamActiveScenarioType.value = type
        updateKamCorridor()
    }

    fun updateKamSla() {
        val baseMargin = _kamCorridorResult.value.activeScenario.marginPct.takeIf { it > 0 } ?: 30.0
        _kamSlaResult.value = KamNegotiationEngine.calculateRetailSlaPenalty(
            dealAmount = kamDealAmount.value.toCleanDouble() ?: 100000.0,
            plannedFillRatePct = kamFillRatePct.value.toCleanDouble() ?: 95.0,
            penaltyRatePct = kamPenaltyRatePct.value.toCleanDouble() ?: 12.0,
            timeSlotPenaltyAmount = kamTimeSlotPenalty.value.toCleanDouble() ?: 1500.0,
            baseGrossMarginPct = baseMargin
        )
        updateKamOnePager()
    }

    fun updateKamOnePager() {
        val fxStress = fxStressProvider()
        val cashGap = cashGapProvider()
        _kamOnePagerSummary.value = KamNegotiationEngine.buildOnePagerSummary(
            corridorResult = _kamCorridorResult.value,
            slaResult = _kamSlaResult.value,
            criticalFxRate = fxStress.marginWipeoutRate,
            fxCurrencyCode = fxStress.foreignCurrencyCode,
            fxSafetyBufferPoints = fxStress.safetyBufferPoints,
            peakCashGapAmount = cashGap.peakCashDeficit,
            corporateWaccRatePct = hurdleRateProvider()
        )
    }

    fun toggleKamOnePager(show: Boolean) {
        showKamOnePagerDialog.value = show
        if (show) updateKamOnePager()
    }

    fun setGiveConcessionType(type: GiveConcessionType) {
        giveConcessionType.value = type
        giveConcessionValue.value = when (type) {
            GiveConcessionType.PAYMENT_DEFERRAL -> "30"
            GiveConcessionType.PRICE_DISCOUNT -> "3"
            GiveConcessionType.RETRO_BONUS -> "2"
            GiveConcessionType.FREIGHT_DELIVERY -> "3.5"
            GiveConcessionType.LISTING_FEE -> "3000"
        }
        updateGiveGetCalculation()
    }

    fun setGiveConcessionValue(value: String) {
        giveConcessionValue.value = value
        updateGiveGetCalculation()
    }

    fun updateGiveGetCalculation() {
        val scenario = _kamCorridorResult.value.activeScenario
        _giveGetResult.value = GiveGetEngine.calculate(
            sellingPrice = scenario.shelfPrice,
            cogs = scenario.cogs,
            baseVolume = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000,
            currentDiscountPct = scenario.frontDiscountPct,
            currentBonusPct = scenario.backBonusPct,
            currentPaymentDays = scenario.paymentDays,
            waccRatePct = hurdleRateProvider(),
            concessionType = giveConcessionType.value,
            concessionValue = giveConcessionValue.value.toCleanDouble() ?: 30.0
        )
    }

    fun updateTradeOff() {
        _tradeOffResult.value = CommercialAnalystEngine.calculateTradeOff(
            dealAmount = tradeOffDealAmount.value.toCleanDouble() ?: 100000.0,
            discountPct = tradeOffDiscountPct.value.toCleanDouble() ?: 3.0,
            deferralDays = tradeOffDeferralDays.value.toCleanInt() ?: 30,
            annualCapitalRatePct = tradeOffAnnualRate.value.toCleanDouble() ?: 20.0
        )
    }

    fun updateBasketItem(id: String, newVolume: Double, newPrice: Double, newCogs: Double) {
        val updated = _basketItems.value.map { item ->
            if (item.id == id) item.copy(volume = newVolume, unitPrice = newPrice, unitCogs = newCogs) else item
        }
        _basketItems.value = updated
        _blendedBasketResult.value = CommercialAnalystEngine.calculateBlendedBasket(updated)
    }

    fun resetBasketToDefaults() {
        _basketItems.value = CommercialAnalystEngine.DEFAULT_BASKET_ITEMS
        _blendedBasketResult.value = CommercialAnalystEngine.calculateBlendedBasket(CommercialAnalystEngine.DEFAULT_BASKET_ITEMS)
    }

    fun updateRebateSimulation() {
        _rebateSimulationResult.value = CommercialAnalystEngine.simulateRebateTiers(
            actualVolume = rebateActualVolume.value.toCleanDouble() ?: 120000.0,
            baseGrossMarginPct = rebateBaseMarginPct.value.toCleanDouble() ?: 30.0
        )
    }

    fun recalculateDealScoring() {
        val isRu = session.appLanguage.value == AppLanguage.RU
        _dealScoringResult.value = DealScoringEngine.calculate(
            sellingPrice = dealApprovalSellingPrice.value.toCleanDouble() ?: 100.0,
            costPrice = dealApprovalCostPrice.value.toCleanDouble() ?: 55.0,
            volumeUnits = dealApprovalVolumeUnits.value.toCleanInt() ?: 1000,
            onInvoiceDiscountPct = dealApprovalDiscountPct.value.toCleanDouble() ?: 0.0,
            offInvoiceBonusPct = dealApprovalBonusPct.value.toCleanDouble() ?: 0.0,
            paymentTermsDays = dealApprovalPaymentDays.value.toCleanInt() ?: 30,
            financingAnnualRatePct = dealApprovalFinancingRate.value.toCleanDouble() ?: 18.0,
            languageRu = isRu
        )
    }

    fun submitDealForApproval() {
        dealApprovalStatus.value = DealApprovalStatus.PENDING_APPROVAL
        session.emitLocalized("Deal submitted for approval (Pending)", "Сделка отправлена на согласование")
    }

    fun approveDeal() {
        dealApprovalStatus.value = DealApprovalStatus.APPROVED
        val currentScoring = _dealScoringResult.value
        val client = selectedClient.value
        val totalDealCost = currentScoring.costPrice * currentScoring.volumeUnits
        val calculatedRoiPct = if (totalDealCost > 0.0) {
            kotlin.math.round(((currentScoring.profitAfterCostOfCapital / totalDealCost) * 100.0) * 100.0) / 100.0
        } else 0.0
        val newDeal = attachPlanFact(
            SavedDeal(
                title = session.dealTitle.value,
                clientName = client?.name ?: session.clientName.value,
                currencyCode = session.activeCurrency.value.code,
                dealValue = currentScoring.totalRevenue,
                interestRatePct = currentScoring.costOfDeferralPct,
                tenureMonths = (currentScoring.paymentTermsDays / 30).coerceAtLeast(1),
                marginPct = currentScoring.netNetMarginPct,
                markupPct = if (currentScoring.costPrice > 0) ((currentScoring.netNetPrice - currentScoring.costPrice) / currentScoring.costPrice) * 100.0 else 0.0,
                roiPct = calculatedRoiPct,
                paybackMonths = currentScoring.paymentTermsDays.toDouble() / 30.0
            )
        )
        scope.launch {
            val saveResult = dealRepository.saveDeal(newDeal)
            val updatedClient = client?.let { clientRepository.attachPastDealToClient(it.id, newDeal) }
            if (updatedClient != null) {
                clientRepository.saveClientToCloud(updatedClient)
            }
            saveResult.onSuccess {
                session.emitLocalized(
                    "Deal approved and saved to client portfolio!",
                    "Сделка утверждена и добавлена в портфель клиента!"
                )
            }.onFailure {
                session.emitLocalized(
                    "Deal approved locally, but cloud sync failed.",
                    "Сделка утверждена локально, но синхронизация с облаком не удалась."
                )
            }
        }
    }

    fun rejectDeal(reason: String = "") {
        dealApprovalStatus.value = DealApprovalStatus.REJECTED
        session.emitLocalized("Deal rejected: $reason", "Сделка отклонена: $reason")
    }

    fun selectClient(clientId: String) {
        selectedClientId.value = clientId
        val client = clientsList.value.firstOrNull { it.id == clientId } ?: return
        session.clientName.value = client.name
        dealApprovalPaymentDays.value = client.agreedPaymentTermsDays.toString()
        dealApprovalDiscountPct.value = client.standardOnInvoiceDiscountPct.toString()
        dealApprovalBonusPct.value = client.standardOffInvoiceBonusPct.toString()
        recalculateDealScoring()
    }

    fun saveClientProfile(profile: ClientNegotiationProfile, isNew: Boolean) {
        if (isNew) {
            clientRepository.addClient(profile)
            selectClient(profile.id)
        } else {
            clientRepository.updateClient(profile)
            if (selectedClientId.value == profile.id) {
                selectClient(profile.id)
            }
        }
        scope.launch {
            clientRepository.saveClientToCloud(profile)
                .onSuccess {
                    if (isNew) {
                        session.emitLocalized(
                            "Client '${profile.name}' added & synced!",
                            "Контрагент '${profile.name}' добавлен и синхронизирован!"
                        )
                    } else {
                        session.emitLocalized(
                            "Client '${profile.name}' updated & synced!",
                            "Данные контрагента '${profile.name}' обновлены и синхронизированы!"
                        )
                    }
                }
                .onFailure {
                    session.emitLocalized(
                        "Client saved locally, but cloud sync failed.",
                        "Контрагент сохранен локально, но облачная синхронизация не удалась."
                    )
                }
        }
    }

    fun deleteClientProfile(clientId: String) {
        val client = clientsList.value.firstOrNull { it.id == clientId }
        val name = client?.name ?: "Client"
        clientRepository.deleteClient(clientId)
        val remaining = clientsList.value
        if (selectedClientId.value == clientId) {
            val next = remaining.firstOrNull()
            if (next != null) {
                selectClient(next.id)
            } else {
                selectedClientId.value = ""
                session.clientName.value = ""
            }
        }
        scope.launch {
            clientRepository.deleteClientFromCloud(clientId)
                .onSuccess {
                    session.emitLocalized("Client '$name' deleted", "Контрагент '$name' удален")
                }
                .onFailure {
                    session.emitLocalized(
                        "Removed locally, but cloud deletion failed.",
                        "Удалено локально, но удаление в облаке не удалось."
                    )
                }
        }
    }

    fun addNegotiationRound(
        clientId: String,
        buyerDemands: String,
        kamCounter: String,
        discountPct: Double,
        bonusPct: Double,
        termsDays: Int,
        outcome: String
    ) {
        val client = clientsList.value.firstOrNull { it.id == clientId } ?: return
        val nextRound = client.negotiationHistory.size + 1
        val touchpoint = NegotiationTouchpoint(
            roundNumber = nextRound,
            discountDemandedPct = discountPct,
            bonusDemandedPct = bonusPct,
            paymentTermsDemandedDays = termsDays,
            buyerDemands = buyerDemands,
            kamCounterOffer = kamCounter,
            outcomeStatus = outcome
        )
        val updated = clientRepository.addNegotiationTouchpoint(clientId, touchpoint)
        scope.launch {
            if (updated != null) {
                clientRepository.saveClientToCloud(updated)
            }
            session.emitLocalized(
                "Round $nextRound logged to client history!",
                "Раунд $nextRound зафиксирован в истории переговоров!"
            )
        }
    }

    fun resetApprovalSession() {
        selectedClientId.value = "client-omnitech-01"
        dealApprovalStatus.value = DealApprovalStatus.DRAFT
    }
}
