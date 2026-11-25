package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.example.data.model.ClientNegotiationProfile
import com.example.data.model.NegotiationTouchpoint
import com.example.data.model.PaymentFrequency
import com.example.data.model.SavedDeal
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ClientNegotiationRepository(
    private var firestore: FirebaseFirestore? = null,
    private val auth: FirebaseAuth? = null,
    private val databaseId: String? = null
) {
    constructor(context: Context) : this(
        firestore = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = FirebaseAuth.getInstance(),
        databaseId = context.applicationContext.getString(R.string.firestore_database_id)
    )

    private val _clients = MutableStateFlow<List<ClientNegotiationProfile>>(emptyList())
    val clients: StateFlow<List<ClientNegotiationProfile>> = _clients.asStateFlow()

    private var clientsListener: ListenerRegistration? = null
    private val idRegex = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

    private val defaultClients: List<ClientNegotiationProfile> = listOf(
        ClientNegotiationProfile(
            id = "client-omnitech-01",
            name = "OmniTech Solutions Inc.",
            industry = "Enterprise IT & Cloud",
            creditLimit = 350000.0,
            agreedPaymentTermsDays = 30,
            standardCurrency = "USD",
            standardOnInvoiceDiscountPct = 5.0,
            maxAllowedDiscountPct = 10.0,
            standardOffInvoiceBonusPct = 6.0,
            annualVolumeRebatePct = 2.5,
            notes = "Стратегический партнер. Платит строго в срок 30 дней. Чувствителен к прозрачности Net-Net цен.",
            pastDeals = listOf(
                SavedDeal(
                    id = "deal-omni-past-01",
                    title = "Q1 Infrastructure License Renewal",
                    clientName = "OmniTech Solutions Inc.",
                    currencyCode = "USD",
                    dealValue = 180000.0,
                    interestRatePct = 5.5,
                    tenureMonths = 12,
                    frequency = PaymentFrequency.MONTHLY,
                    marginPct = 48.0,
                    updatedAt = System.currentTimeMillis() - 7776000000L
                ),
                SavedDeal(
                    id = "deal-omni-past-02",
                    title = "Enterprise Cloud Support Addendum",
                    clientName = "OmniTech Solutions Inc.",
                    currencyCode = "USD",
                    dealValue = 65000.0,
                    interestRatePct = 5.0,
                    tenureMonths = 6,
                    frequency = PaymentFrequency.MONTHLY,
                    marginPct = 52.0,
                    updatedAt = System.currentTimeMillis() - 15552000000L
                )
            ),
            negotiationHistory = listOf(
                NegotiationTouchpoint(
                    id = "omni-round-1",
                    roundNumber = 1,
                    dateTimestamp = System.currentTimeMillis() - 1209600000L,
                    discountDemandedPct = 12.0,
                    bonusDemandedPct = 8.0,
                    paymentTermsDemandedDays = 60,
                    buyerDemands = "Закупщик запросил 12% фронт-скидку и отсрочку 60 дней для включения в годовой бюджет.",
                    kamCounterOffer = "КАМ отклонил 60 дней (стоимость отсрочки съедает 3% маржи). Предложено 5% скидки при строгой оплате за 30 дней.",
                    outcomeStatus = "COUNTER_OFFER_PENDING"
                ),
                NegotiationTouchpoint(
                    id = "omni-round-2",
                    roundNumber = 2,
                    dateTimestamp = System.currentTimeMillis() - 432000000L,
                    discountDemandedPct = 6.0,
                    bonusDemandedPct = 6.0,
                    paymentTermsDemandedDays = 30,
                    buyerDemands = "Клиент согласился на 30 дней, но запросил 6% ретро-бонус по итогам года при объеме от 1500 шт.",
                    kamCounterOffer = "Принято с привязкой к жесткому KPI по объему. Согласована Net-Net маржа 38%.",
                    outcomeStatus = "TERMS_LOCKED"
                )
            )
        ),
        ClientNegotiationProfile(
            id = "client-metropolis-02",
            name = "Metropolis Hypermarket Group",
            industry = "FMCG & National Retail",
            creditLimit = 800000.0,
            agreedPaymentTermsDays = 65,
            standardCurrency = "USD",
            standardOnInvoiceDiscountPct = 8.0,
            maxAllowedDiscountPct = 14.0,
            standardOffInvoiceBonusPct = 12.0,
            annualVolumeRebatePct = 4.0,
            notes = "Крупная федеральная сеть. Жесткие требования по отсрочке (60-75 дней) и бэк-бонусам (10-14%). Высокий риск заморозки оборотного капитала.",
            pastDeals = listOf(
                SavedDeal(
                    id = "deal-metro-past-01",
                    title = "Annual Shelf Listing Contract",
                    clientName = "Metropolis Hypermarket Group",
                    currencyCode = "USD",
                    dealValue = 420000.0,
                    interestRatePct = 7.5,
                    tenureMonths = 12,
                    frequency = PaymentFrequency.MONTHLY,
                    marginPct = 26.5,
                    updatedAt = System.currentTimeMillis() - 5184000000L
                )
            ),
            negotiationHistory = listOf(
                NegotiationTouchpoint(
                    id = "metro-round-1",
                    roundNumber = 1,
                    dateTimestamp = System.currentTimeMillis() - 2592000000L,
                    discountDemandedPct = 15.0,
                    bonusDemandedPct = 14.0,
                    paymentTermsDemandedDays = 90,
                    buyerDemands = "Требование 90 дней отсрочки + 14% маркетинг-бонус + 15% скидка. Пограничная зона (уничтожение маржи).",
                    kamCounterOffer = "КАМ выставил расчет стоимости отсрочки: при 90 днях компания теряет 5.2% стоимости контракта на финансировании.",
                    outcomeStatus = "CONCESSION_GRANTED"
                ),
                NegotiationTouchpoint(
                    id = "metro-round-2",
                    roundNumber = 2,
                    dateTimestamp = System.currentTimeMillis() - 864000000L,
                    discountDemandedPct = 8.0,
                    bonusDemandedPct = 12.0,
                    paymentTermsDemandedDays = 65,
                    buyerDemands = "Компромисс: отсрочка 65 дней, 8% он-инвойс, 12% офф-инвойс ретро при гарантии 4 торцевых промо-выкладок в год.",
                    kamCounterOffer = "Согласовано с обязательным trade-off: включение в контракт двух премиальных высокомаржинальных позиций.",
                    outcomeStatus = "TERMS_LOCKED"
                )
            )
        ),
        ClientNegotiationProfile(
            id = "client-vanguard-03",
            name = "Vanguard Financial AG",
            industry = "Banking & FinTech",
            creditLimit = 500000.0,
            agreedPaymentTermsDays = 21,
            standardCurrency = "EUR",
            standardOnInvoiceDiscountPct = 3.5,
            maxAllowedDiscountPct = 8.0,
            standardOffInvoiceBonusPct = 4.0,
            annualVolumeRebatePct = 1.5,
            notes = "Европейский финтех. Высокая платежная дисциплина (оплата в течение 21 дня). Низкие требования по скидкам.",
            pastDeals = listOf(
                SavedDeal(
                    id = "deal-van-past-01",
                    title = "EMEA Secure Gateway Rollout",
                    clientName = "Vanguard Financial AG",
                    currencyCode = "EUR",
                    dealValue = 95000.0,
                    interestRatePct = 4.8,
                    tenureMonths = 24,
                    frequency = PaymentFrequency.QUARTERLY,
                    marginPct = 44.5,
                    updatedAt = System.currentTimeMillis() - 172800000
                )
            ),
            negotiationHistory = listOf(
                NegotiationTouchpoint(
                    id = "van-round-1",
                    roundNumber = 1,
                    dateTimestamp = System.currentTimeMillis() - 1728000000L,
                    discountDemandedPct = 5.0,
                    bonusDemandedPct = 4.0,
                    paymentTermsDemandedDays = 21,
                    buyerDemands = "Запрос оптовой скидки 5% при авансировании 20% сделки.",
                    kamCounterOffer = "Согласовано 3.5% скидки + 4% накопительный годовой бонус. Зеленый коридор сделки.",
                    outcomeStatus = "TERMS_LOCKED"
                )
            )
        )
    )

    init {
        _clients.value = defaultClients
    }

    @Suppress("UNUSED_PARAMETER")
    fun startFirestoreSync(scope: CoroutineScope) {
        val db = firestore ?: return
        val currentAuth = auth ?: return
        val uid = currentAuth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return
        clientsListener?.remove()

        val path = "users/$uid/clients"
        clientsListener = db.collection("users").document(uid).collection("clients")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (currentAuth.currentUser?.uid == uid) {
                        handleFirestoreError(error, FirestoreOperationType.LIST, path)
                    }
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val remoteClients = snapshot.documents.mapNotNull { doc ->
                        try {
                            val rawPast = (doc.get("pastDeals") as? List<*>)?.mapNotNull { entry ->
                                val m = entry as? Map<*, *> ?: return@mapNotNull null
                                val rawFreq = m["frequency"] as? String
                                val freq = enumValues<PaymentFrequency>()
                                    .firstOrNull { it.name == rawFreq } ?: PaymentFrequency.MONTHLY
                                SavedDeal(
                                    id = (m["id"] as? String).orEmpty().ifBlank { UUID.randomUUID().toString() },
                                    title = (m["title"] as? String) ?: "Deal",
                                    clientName = (m["clientName"] as? String) ?: "",
                                    currencyCode = (m["currencyCode"] as? String) ?: "USD",
                                    dealValue = (m["dealValue"] as? Number)?.toDouble() ?: 0.0,
                                    marginPct = (m["marginPct"] as? Number)?.toDouble() ?: 0.0,
                                    roiPct = (m["roiPct"] as? Number)?.toDouble() ?: 0.0,
                                    frequency = freq,
                                    updatedAt = (m["updatedAt"] as? Number)?.toLong() ?: 0L,
                                    createdBy = uid
                                )
                            } ?: emptyList()

                            val rawHistory = (doc.get("negotiationHistory") as? List<*>)?.mapNotNull { entry ->
                                val m = entry as? Map<*, *> ?: return@mapNotNull null
                                NegotiationTouchpoint(
                                    id = (m["id"] as? String).orEmpty().ifBlank { UUID.randomUUID().toString() },
                                    roundNumber = (m["roundNumber"] as? Number)?.toInt() ?: 1,
                                    dateTimestamp = (m["dateTimestamp"] as? Number)?.toLong() ?: 0L,
                                    discountDemandedPct = (m["discountDemandedPct"] as? Number)?.toDouble() ?: 0.0,
                                    bonusDemandedPct = (m["bonusDemandedPct"] as? Number)?.toDouble() ?: 0.0,
                                    paymentTermsDemandedDays = (m["paymentTermsDemandedDays"] as? Number)?.toInt() ?: 30,
                                    buyerDemands = (m["buyerDemands"] as? String) ?: "",
                                    kamCounterOffer = (m["kamCounterOffer"] as? String) ?: "",
                                    outcomeStatus = (m["outcomeStatus"] as? String) ?: "COUNTER_OFFER_PENDING"
                                )
                            } ?: emptyList()

                            ClientNegotiationProfile(
                                id = doc.getString("id") ?: doc.id,
                                name = doc.getString("name") ?: "Client",
                                industry = doc.getString("industry") ?: "",
                                creditLimit = doc.getDouble("creditLimit") ?: 0.0,
                                agreedPaymentTermsDays = (doc.getLong("agreedPaymentTermsDays") ?: 30L).toInt(),
                                standardCurrency = doc.getString("standardCurrency") ?: "USD",
                                standardOnInvoiceDiscountPct = doc.getDouble("standardOnInvoiceDiscountPct") ?: 0.0,
                                maxAllowedDiscountPct = doc.getDouble("maxAllowedDiscountPct") ?: 0.0,
                                standardOffInvoiceBonusPct = doc.getDouble("standardOffInvoiceBonusPct") ?: 0.0,
                                annualVolumeRebatePct = doc.getDouble("annualVolumeRebatePct") ?: 0.0,
                                notes = doc.getString("notes") ?: "",
                                pastDeals = rawPast,
                                negotiationHistory = rawHistory
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    _clients.value = (remoteClients + defaultClients).distinctBy { it.id }
                }
            }
    }

    fun stopFirestoreSync() {
        clientsListener?.remove()
        clientsListener = null
    }

    fun refreshFirestoreInstanceAfterReset() {
        databaseId?.takeIf { it.isNotBlank() }?.let { dbId ->
            firestore = FirebaseFirestore.getInstance(dbId)
        }
    }

    fun resetToDefaults() {
        stopFirestoreSync()
        _clients.value = defaultClients
    }

    fun addClient(profile: ClientNegotiationProfile) {
        val current = _clients.value.toMutableList()
        current.removeAll { it.id == profile.id }
        current.add(0, profile)
        _clients.value = current
    }

    fun updateClient(profile: ClientNegotiationProfile) {
        val current = _clients.value.toMutableList()
        val index = current.indexOfFirst { it.id == profile.id }
        if (index >= 0) {
            current[index] = profile
            _clients.value = current
        } else {
            addClient(profile)
        }
    }

    fun deleteClient(clientId: String) {
        val current = _clients.value.toMutableList()
        current.removeAll { it.id == clientId }
        _clients.value = current
    }

    fun addNegotiationTouchpoint(clientId: String, touchpoint: NegotiationTouchpoint): ClientNegotiationProfile? {
        val current = _clients.value.toMutableList()
        val index = current.indexOfFirst { it.id == clientId }
        if (index >= 0) {
            val client = current[index]
            val updated = client.copy(negotiationHistory = listOf(touchpoint) + client.negotiationHistory)
            current[index] = updated
            _clients.value = current
            return updated
        }
        return null
    }

    fun attachPastDealToClient(clientId: String, deal: SavedDeal): ClientNegotiationProfile? {
        val current = _clients.value.toMutableList()
        val index = current.indexOfFirst { it.id == clientId }
        if (index >= 0) {
            val client = current[index]
            val updated = client.copy(pastDeals = listOf(deal) + client.pastDeals)
            current[index] = updated
            _clients.value = current
            return updated
        }
        return null
    }

    suspend fun saveClientToCloud(profile: ClientNegotiationProfile): Result<Unit> {
        val db = firestore ?: return Result.success(Unit)
        val uid = auth?.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val safeProfile = sanitizeForRules(profile)
        val docPath = "users/$uid/clients/${safeProfile.id}"

        return try {
            val payload = hashMapOf<String, Any>(
                "id" to safeProfile.id,
                "createdBy" to uid,
                "name" to safeProfile.name,
                "industry" to safeProfile.industry,
                "creditLimit" to safeProfile.creditLimit,
                "agreedPaymentTermsDays" to safeProfile.agreedPaymentTermsDays,
                "standardCurrency" to safeProfile.standardCurrency,
                "standardOnInvoiceDiscountPct" to safeProfile.standardOnInvoiceDiscountPct,
                "maxAllowedDiscountPct" to safeProfile.maxAllowedDiscountPct,
                "standardOffInvoiceBonusPct" to safeProfile.standardOffInvoiceBonusPct,
                "annualVolumeRebatePct" to safeProfile.annualVolumeRebatePct,
                "notes" to safeProfile.notes,
                "updatedAt" to System.currentTimeMillis(),
                "pastDeals" to safeProfile.pastDeals.take(50).map { d ->
                    hashMapOf<String, Any>(
                        "id" to d.id,
                        "title" to d.title.take(150),
                        "clientName" to d.clientName.take(150),
                        "currencyCode" to d.currencyCode.take(10),
                        "dealValue" to if (d.dealValue.isFinite()) d.dealValue else 0.0,
                        "marginPct" to if (d.marginPct.isFinite()) d.marginPct else 0.0,
                        "roiPct" to if (d.roiPct.isFinite()) d.roiPct else 0.0,
                        "frequency" to d.frequency.name,
                        "updatedAt" to d.updatedAt
                    )
                },
                "negotiationHistory" to safeProfile.negotiationHistory.take(100).map { t ->
                    hashMapOf<String, Any>(
                        "id" to (if (idRegex.matches(t.id)) t.id else UUID.randomUUID().toString()),
                        "roundNumber" to t.roundNumber,
                        "dateTimestamp" to t.dateTimestamp,
                        "discountDemandedPct" to if (t.discountDemandedPct.isFinite()) t.discountDemandedPct else 0.0,
                        "bonusDemandedPct" to if (t.bonusDemandedPct.isFinite()) t.bonusDemandedPct else 0.0,
                        "paymentTermsDemandedDays" to t.paymentTermsDemandedDays,
                        "buyerDemands" to t.buyerDemands.take(500),
                        "kamCounterOffer" to t.kamCounterOffer.take(500),
                        "outcomeStatus" to t.outcomeStatus.take(64)
                    )
                }
            )
            db.collection("users").document(uid).collection("clients")
                .document(safeProfile.id)
                .set(payload)
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteClientFromCloud(clientId: String): Result<Unit> {
        val db = firestore ?: return Result.success(Unit)
        val uid = auth?.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return Result.success(Unit)
        val docPath = "users/$uid/clients/$clientId"

        return try {
            if (idRegex.matches(clientId)) {
                db.collection("users").document(uid).collection("clients")
                    .document(clientId)
                    .delete()
                    .await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, FirestoreOperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun sanitizeForRules(profile: ClientNegotiationProfile): ClientNegotiationProfile {
        fun safePct(value: Double): Double {
            val finite = if (value.isFinite()) value else 0.0
            return finite.coerceIn(0.0, 100.0)
        }
        val safeId = if (idRegex.matches(profile.id)) profile.id else UUID.randomUUID().toString()
        val safeCredit = (if (profile.creditLimit.isFinite()) profile.creditLimit else 0.0)
            .coerceIn(0.0, 1_000_000_000_000.0)
        return profile.copy(
            id = safeId,
            name = profile.name.trim().ifBlank { "Client" }.take(150),
            industry = profile.industry.trim().take(150),
            creditLimit = safeCredit,
            agreedPaymentTermsDays = profile.agreedPaymentTermsDays.coerceIn(0, 3650),
            standardCurrency = profile.standardCurrency.trim().uppercase().ifBlank { "USD" }.take(10),
            standardOnInvoiceDiscountPct = safePct(profile.standardOnInvoiceDiscountPct),
            maxAllowedDiscountPct = safePct(profile.maxAllowedDiscountPct),
            standardOffInvoiceBonusPct = safePct(profile.standardOffInvoiceBonusPct),
            annualVolumeRebatePct = safePct(profile.annualVolumeRebatePct),
            notes = profile.notes.trim().take(2000),
            pastDeals = profile.pastDeals.take(50),
            negotiationHistory = profile.negotiationHistory.take(100)
        )
    }

    private fun handleFirestoreError(
        exception: Throwable,
        operationType: FirestoreOperationType,
        path: String?
    ) {
        val causes = generateSequence(exception) { it.cause }
        val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
            ?: causes.firstOrNull { it !is CancellationException }
            ?: exception

        if (BuildConfig.DEBUG) {
            val currentUser = auth?.currentUser
            val providerInfoList = currentUser?.providerData?.map { provider ->
                JSONObject().apply {
                    put("providerId", provider.providerId)
                    put("email", provider.email)
                }
            } ?: emptyList()
            val authInfoJson = JSONObject().apply {
                put("userId", currentUser?.uid)
                put("email", currentUser?.email)
                put("providerInfo", JSONArray(providerInfoList))
            }
            val errorInfoJson = JSONObject().apply {
                put("error", rootError.message ?: rootError.toString())
                put("operationType", operationType.value)
                put("path", path)
                put("authInfo", authInfoJson)
            }
            Log.e("ClientFirestoreError", "Firestore Error: $errorInfoJson")
        } else {
            Log.e("ClientFirestoreError", "Firestore Error [${operationType.value}] at $path: ${rootError.javaClass.simpleName}")
        }
    }
}
