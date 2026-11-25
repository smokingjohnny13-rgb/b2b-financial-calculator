package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
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
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class FirestoreOperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

class DealRepository(
    private var firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val databaseId: String? = null
) {
    constructor(context: Context) : this(
        firestore = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        databaseId = context.applicationContext.getString(R.string.firestore_database_id)
    )

    private val _deals = MutableStateFlow<List<SavedDeal>>(emptyList())
    val deals: StateFlow<List<SavedDeal>> = _deals.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var dealsListener: ListenerRegistration? = null

    private val idRegex = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

    private val defaultPresets: List<SavedDeal> = listOf(
        SavedDeal(
            id = "deal-enterprise-saas-01",
            title = "Global Enterprise Platform License",
            clientName = "OmniTech Solutions Inc.",
            currencyCode = "USD",
            dealValue = 180000.0,
            interestRatePct = 5.5,
            tenureMonths = 36,
            frequency = PaymentFrequency.MONTHLY,
            periodicPayment = 5437.42,
            ltv = 240000.0,
            ltvCacRatio = 4.8,
            marginPct = 52.0,
            markupPct = 108.3,
            roiPct = 168.0,
            paybackMonths = 7.1,
            updatedAt = System.currentTimeMillis() - 86400000,
            createdBy = "local_executive"
        ),
        SavedDeal(
            id = "deal-emea-expansion-02",
            title = "EMEA Multi-Region Cloud Migration",
            clientName = "Vanguard Financial AG",
            currencyCode = "EUR",
            dealValue = 95000.0,
            interestRatePct = 4.8,
            tenureMonths = 24,
            frequency = PaymentFrequency.QUARTERLY,
            periodicPayment = 12510.35,
            ltv = 150000.0,
            ltvCacRatio = 3.6,
            marginPct = 44.5,
            markupPct = 80.2,
            roiPct = 132.5,
            paybackMonths = 9.2,
            updatedAt = System.currentTimeMillis() - 172800000,
            createdBy = "local_executive"
        ),
        SavedDeal(
            id = "deal-apac-retail-03",
            title = "APAC Retail Core Transformation",
            clientName = "Aegis Retail Network",
            currencyCode = "GBP",
            dealValue = 240000.0,
            interestRatePct = 6.2,
            tenureMonths = 48,
            frequency = PaymentFrequency.ANNUALLY,
            periodicPayment = 69820.10,
            ltv = 360000.0,
            ltvCacRatio = 5.4,
            marginPct = 58.0,
            markupPct = 138.1,
            roiPct = 210.0,
            paybackMonths = 5.8,
            updatedAt = System.currentTimeMillis() - 259200000,
            createdBy = "local_executive"
        )
    )

    init {
        _deals.value = defaultPresets
    }

    @Suppress("UNUSED_PARAMETER")
    fun startFirestoreSync(scope: CoroutineScope) {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() } ?: return
        dealsListener?.remove()

        val path = "users/$uid/deals"
        dealsListener = firestore.collection("users").document(uid).collection("deals")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(error, FirestoreOperationType.LIST, path)
                    }
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val remoteDeals = snapshot.documents.mapNotNull { doc ->
                        try {
                            val rawFrequency = doc.getString("frequency")
                            val parsedFrequency = enumValues<PaymentFrequency>()
                                .firstOrNull { it.name == rawFrequency } ?: PaymentFrequency.MONTHLY
                            SavedDeal(
                                id = doc.getString("id") ?: doc.id,
                                title = doc.getString("title") ?: "Untitled Deal",
                                clientName = doc.getString("clientName") ?: "Client",
                                currencyCode = doc.getString("currencyCode") ?: "USD",
                                dealValue = doc.getDouble("dealValue") ?: 0.0,
                                interestRatePct = doc.getDouble("interestRatePct") ?: 0.0,
                                tenureMonths = (doc.getLong("tenureMonths") ?: 12L).toInt(),
                                frequency = parsedFrequency,
                                periodicPayment = doc.getDouble("periodicPayment") ?: 0.0,
                                ltv = doc.getDouble("ltv") ?: 0.0,
                                ltvCacRatio = doc.getDouble("ltvCacRatio") ?: 0.0,
                                marginPct = doc.getDouble("marginPct") ?: 0.0,
                                markupPct = doc.getDouble("markupPct") ?: 0.0,
                                roiPct = doc.getDouble("roiPct") ?: 0.0,
                                paybackMonths = doc.getDouble("paybackMonths") ?: 0.0,
                                updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
                                createdBy = uid,
                                hasPlanFactData = doc.getBoolean("hasPlanFactData") ?: false,
                                planFactPlanPrice = doc.getDouble("planFactPlanPrice") ?: 100.0,
                                planFactPlanVolume = (doc.getLong("planFactPlanVolume") ?: 1000L).toInt(),
                                planFactPlanCost = doc.getDouble("planFactPlanCost") ?: 60.0,
                                planFactPlanDays = (doc.getLong("planFactPlanDays") ?: 30L).toInt(),
                                planFactActualPrice = doc.getDouble("planFactActualPrice") ?: 96.0,
                                planFactActualVolume = (doc.getLong("planFactActualVolume") ?: 1050L).toInt(),
                                planFactActualCost = doc.getDouble("planFactActualCost") ?: 63.0,
                                planFactActualDays = (doc.getLong("planFactActualDays") ?: 48L).toInt(),
                                planFactActualPenalties = doc.getDouble("planFactActualPenalties") ?: 0.0,
                                planFactCapitalRate = doc.getDouble("planFactCapitalRate") ?: 18.0
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                    // Always reconcile remote deals (including empty list when last cloud deal is deleted)
                    val merged = (remoteDeals + defaultPresets).distinctBy { it.id }
                    _deals.value = merged.sortedByDescending { it.updatedAt }
                }
            }
    }

    fun stopFirestoreSync() {
        dealsListener?.remove()
        dealsListener = null
    }

    suspend fun resetForSignOut() {
        stopFirestoreSync()
        _deals.value = defaultPresets
        _isSyncing.value = false
        try {
            firestore.terminate().await()
            firestore.clearPersistence().await()
            databaseId?.takeIf { it.isNotBlank() }?.let { dbId ->
                firestore = FirebaseFirestore.getInstance(dbId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DealRepo", "Could not clear Firestore offline cache on sign-out: ${e.message}")
        }
    }

    suspend fun saveDeal(deal: SavedDeal): Result<Unit> {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
        val safeDeal = sanitizeForRules(deal, uid ?: deal.createdBy)
        val docPath = if (uid != null) "users/$uid/deals/${safeDeal.id}" else "local/deals/${safeDeal.id}"

        return try {
            _isSyncing.value = true
            // Update local state immediately
            val current = _deals.value.toMutableList()
            val existingIndex = current.indexOfFirst { it.id == safeDeal.id }
            if (existingIndex >= 0) {
                current[existingIndex] = safeDeal
            } else {
                current.add(0, safeDeal)
            }
            _deals.value = current.sortedByDescending { it.updatedAt }

            // Sync to Firestore if authenticated
            if (uid != null) {
                val data = hashMapOf<String, Any>(
                    "id" to safeDeal.id,
                    "title" to safeDeal.title,
                    "clientName" to safeDeal.clientName,
                    "currencyCode" to safeDeal.currencyCode,
                    "dealValue" to safeDeal.dealValue,
                    "interestRatePct" to safeDeal.interestRatePct,
                    "tenureMonths" to safeDeal.tenureMonths,
                    "frequency" to safeDeal.frequency.name,
                    "periodicPayment" to safeDeal.periodicPayment,
                    "ltv" to safeDeal.ltv,
                    "ltvCacRatio" to safeDeal.ltvCacRatio,
                    "marginPct" to safeDeal.marginPct,
                    "markupPct" to safeDeal.markupPct,
                    "roiPct" to safeDeal.roiPct,
                    "paybackMonths" to safeDeal.paybackMonths,
                    "updatedAt" to safeDeal.updatedAt,
                    "createdBy" to uid,
                    "hasPlanFactData" to safeDeal.hasPlanFactData,
                    "planFactPlanPrice" to safeDeal.planFactPlanPrice,
                    "planFactPlanVolume" to safeDeal.planFactPlanVolume,
                    "planFactPlanCost" to safeDeal.planFactPlanCost,
                    "planFactPlanDays" to safeDeal.planFactPlanDays,
                    "planFactActualPrice" to safeDeal.planFactActualPrice,
                    "planFactActualVolume" to safeDeal.planFactActualVolume,
                    "planFactActualCost" to safeDeal.planFactActualCost,
                    "planFactActualDays" to safeDeal.planFactActualDays,
                    "planFactActualPenalties" to safeDeal.planFactActualPenalties,
                    "planFactCapitalRate" to safeDeal.planFactCapitalRate
                )
                firestore.collection("users").document(uid).collection("deals")
                    .document(safeDeal.id)
                    .set(data)
                    .await()
            }
            _isSyncing.value = false
            Result.success(Unit)
        } catch (e: CancellationException) {
            _isSyncing.value = false
            throw e
        } catch (e: Exception) {
            _isSyncing.value = false
            handleFirestoreError(e, FirestoreOperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteDeal(dealId: String): Result<Unit> {
        val uid = auth.currentUser?.uid?.takeIf { it.isNotBlank() }
        val docPath = if (uid != null) "users/$uid/deals/$dealId" else "local/deals/$dealId"

        return try {
            val current = _deals.value.toMutableList()
            current.removeAll { it.id == dealId }
            _deals.value = current

            if (uid != null && idRegex.matches(dealId)) {
                firestore.collection("users").document(uid).collection("deals")
                    .document(dealId)
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

    private fun sanitizeForRules(deal: SavedDeal, ownerId: String): SavedDeal {
        fun safeDouble(value: Double, min: Double, max: Double): Double {
            val finite = if (value.isFinite()) value else 0.0
            return finite.coerceIn(min, max)
        }
        val safeId = if (idRegex.matches(deal.id)) deal.id else UUID.randomUUID().toString()
        return deal.copy(
            id = safeId,
            title = deal.title.trim().ifBlank { "Untitled Deal" }.take(150),
            clientName = deal.clientName.trim().ifBlank { "Client" }.take(150),
            currencyCode = deal.currencyCode.trim().uppercase().ifBlank { "USD" }.take(10),
            dealValue = safeDouble(deal.dealValue, 0.0, 1_000_000_000_000.0),
            interestRatePct = safeDouble(deal.interestRatePct, 0.0, 1000.0),
            tenureMonths = deal.tenureMonths.coerceIn(1, 600),
            periodicPayment = safeDouble(deal.periodicPayment, 0.0, 1_000_000_000_000.0),
            ltv = safeDouble(deal.ltv, 0.0, 1_000_000_000_000.0),
            ltvCacRatio = safeDouble(deal.ltvCacRatio, 0.0, 100_000.0),
            marginPct = safeDouble(deal.marginPct, -1000.0, 100.0),
            markupPct = safeDouble(deal.markupPct, -100.0, 100_000.0),
            roiPct = safeDouble(deal.roiPct, -1000.0, 1_000_000.0),
            paybackMonths = safeDouble(deal.paybackMonths, 0.0, 100_000.0),
            updatedAt = deal.updatedAt.coerceAtLeast(0L),
            createdBy = ownerId,
            planFactPlanPrice = safeDouble(deal.planFactPlanPrice, 0.0, 1_000_000_000_000.0),
            planFactPlanVolume = deal.planFactPlanVolume.coerceAtLeast(0),
            planFactPlanCost = safeDouble(deal.planFactPlanCost, 0.0, 1_000_000_000_000.0),
            planFactPlanDays = deal.planFactPlanDays.coerceIn(0, 3650),
            planFactActualPrice = safeDouble(deal.planFactActualPrice, 0.0, 1_000_000_000_000.0),
            planFactActualVolume = deal.planFactActualVolume.coerceAtLeast(0),
            planFactActualCost = safeDouble(deal.planFactActualCost, 0.0, 1_000_000_000_000.0),
            planFactActualDays = deal.planFactActualDays.coerceIn(0, 3650),
            planFactActualPenalties = safeDouble(deal.planFactActualPenalties, 0.0, 1_000_000_000_000.0),
            planFactCapitalRate = safeDouble(deal.planFactCapitalRate, 0.0, 1000.0)
        )
    }

    private fun handleFirestoreError(
        exception: Throwable,
        operationType: FirestoreOperationType,
        path: String?
    ): String {
        val causes = generateSequence(exception) { it.cause }
        val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
            ?: causes.firstOrNull { it !is CancellationException }
            ?: exception

        val currentUser = auth.currentUser
        val providerInfoList = currentUser?.providerData?.map { provider ->
            JSONObject().apply {
                put("providerId", provider.providerId)
                put("email", if (BuildConfig.DEBUG) provider.email else "[REDACTED]")
            }
        } ?: emptyList()

        val authInfoJson = JSONObject().apply {
            put("userId", currentUser?.uid)
            put("email", if (BuildConfig.DEBUG) currentUser?.email else "[REDACTED]")
            put("emailVerified", currentUser?.isEmailVerified)
            put("tenantId", currentUser?.tenantId)
            put("providerInfo", JSONArray(providerInfoList))
        }

        val errorInfoJson = JSONObject().apply {
            put("error", rootError.message ?: rootError.toString())
            put("operationType", operationType.value)
            put("path", path)
            put("authInfo", authInfoJson)
        }

        val jsonString = errorInfoJson.toString()
        if (BuildConfig.DEBUG) {
            Log.e("FirestoreError", "Firestore Error: $jsonString")
        } else {
            Log.e("FirestoreError", "Firestore Error [${operationType.value}] at $path: ${rootError.javaClass.simpleName}")
        }
        return jsonString
    }
}
