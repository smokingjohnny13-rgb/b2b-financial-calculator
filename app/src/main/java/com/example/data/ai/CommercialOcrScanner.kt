package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "CommercialOcrScanner"

data class OcrExtractedDealData(
    val clientName: String = "",
    val documentType: String = "",
    val shelfPriceRsp: Double? = null,
    val vatRatePct: Double? = 20.0,
    val retailerMarginPct: Double? = null,
    val frontDiscountPct: Double? = null,
    val backBonusPct: Double? = null,
    val logisticsCostPerUnit: Double? = null,
    val supplierCogs: Double? = null,
    val paymentTermsDays: Int? = null,
    val contractVolumeUnits: Int? = null,
    val currencyCode: String = "RUB",
    val summaryFindings: String = ""
)

class CommercialOcrScanner {

    private val ocrSchema = Schema.obj(
        properties = mapOf(
            "clientName" to Schema.string(description = "Identified company, retail chain, supermarket or counterparty name"),
            "documentType" to Schema.string(description = "Contract annex, specification, price list, invoice, or protocol"),
            "shelfPriceRsp" to Schema.double(description = "Retail shelf price with VAT or base list price if found", nullable = true),
            "vatRatePct" to Schema.double(description = "VAT tax rate percentage e.g. 20.0 or 10.0 or 0.0", nullable = true),
            "retailerMarginPct" to Schema.double(description = "Retailer margin markup percentage if mentioned", nullable = true),
            "frontDiscountPct" to Schema.double(description = "On-invoice wholesale discount percentage", nullable = true),
            "backBonusPct" to Schema.double(description = "Off-invoice retro bonus or marketing rebate percentage", nullable = true),
            "logisticsCostPerUnit" to Schema.double(description = "Delivery or logistics cost per unit", nullable = true),
            "supplierCogs" to Schema.double(description = "Supplier purchase cost, unit COGS or factory price", nullable = true),
            "paymentTermsDays" to Schema.integer(description = "Payment deferral period in days e.g. 14, 30, 45, 60, 90", nullable = true),
            "contractVolumeUnits" to Schema.integer(description = "Contracted batch volume in units or pieces", nullable = true),
            "currencyCode" to Schema.string(description = "Currency code: RUB, USD, EUR, KZT, BYN, CNY, etc."),
            "summaryFindings" to Schema.string(description = "Brief 1-2 sentence commercial executive summary of extracted terms")
        ),
        optionalProperties = listOf(
            "shelfPriceRsp", "vatRatePct", "retailerMarginPct",
            "frontDiscountPct", "backBonusPct", "logisticsCostPerUnit",
            "supplierCogs", "paymentTermsDays", "contractVolumeUnits"
        )
    )

    private val modelConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = ocrSchema
        temperature = 0.1f
    }

    private val generativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = modelConfig,
            systemInstruction = content {
                text(
                    """
                    You are a veteran Key Account Manager and Commercial Analyst.
                    Your task is to accurately extract B2B commercial terms, pricing waterfalls, and negotiation conditions
                    from images of scanned contracts, price lists, specification annexes, retail chain purchase orders, or protocols of disagreements.
                    
                    Extract the following with strict precision:
                    - Client / Retail Chain name (e.g. X5, Magnit, Lenta, Auchan, Wildberries, Ozon, Metro, DNS, or private corporate accounts)
                    - Shelf RSP (РРЦ / Полочная цена с НДС)
                    - VAT rate (НДС %: 20%, 10%, or 0%)
                    - Retailer Margin % (Наценка / торговая маржа сети)
                    - Front on-invoice discount (Скидка в накладной / скидка от базового прайса)
                    - Back off-invoice bonuses (Ретро-бонус, маркетинговая премия, плата за услуги продвижения)
                    - Logistics / Delivery fee per unit (Доставка до РЦ)
                    - Supplier unit cost (Себестоимость / входная цена)
                    - Payment terms (Отсрочка платежа в календарных или банковских днях)
                    - Volume in units/pieces (Объем поставки / квант)
                    - Currency code (RUB, USD, EUR, etc.)
                    
                    Return clean numbers only. If a parameter is not mentioned in the document, return null for that optional field.
                    """
                )
            }
        )
    }

    suspend fun scanDocumentImage(bitmap: Bitmap): Result<OcrExtractedDealData> {
        return runCatching {
            withTimeout(60_000L) {
                val promptContent = content {
                    image(bitmap)
                    text("Extract all commercial contract conditions, price waterfall numbers, payment terms, and client details from this document image.")
                }

                val response = generativeModel.generateContent(promptContent)
                val jsonString = response.text ?: throw IllegalStateException("Model returned empty text.")

                Log.d(TAG, "OCR JSON response: $jsonString")
                val json = JSONObject(jsonString)

                OcrExtractedDealData(
                    clientName = json.optString("clientName", ""),
                    documentType = json.optString("documentType", "Commercial Document"),
                    shelfPriceRsp = if (json.has("shelfPriceRsp") && !json.isNull("shelfPriceRsp")) json.getDouble("shelfPriceRsp") else null,
                    vatRatePct = if (json.has("vatRatePct") && !json.isNull("vatRatePct")) json.getDouble("vatRatePct") else 20.0,
                    retailerMarginPct = if (json.has("retailerMarginPct") && !json.isNull("retailerMarginPct")) json.getDouble("retailerMarginPct") else null,
                    frontDiscountPct = if (json.has("frontDiscountPct") && !json.isNull("frontDiscountPct")) json.getDouble("frontDiscountPct") else null,
                    backBonusPct = if (json.has("backBonusPct") && !json.isNull("backBonusPct")) json.getDouble("backBonusPct") else null,
                    logisticsCostPerUnit = if (json.has("logisticsCostPerUnit") && !json.isNull("logisticsCostPerUnit")) json.getDouble("logisticsCostPerUnit") else null,
                    supplierCogs = if (json.has("supplierCogs") && !json.isNull("supplierCogs")) json.getDouble("supplierCogs") else null,
                    paymentTermsDays = if (json.has("paymentTermsDays") && !json.isNull("paymentTermsDays")) json.getInt("paymentTermsDays") else null,
                    contractVolumeUnits = if (json.has("contractVolumeUnits") && !json.isNull("contractVolumeUnits")) json.getInt("contractVolumeUnits") else null,
                    currencyCode = json.optString("currencyCode", "RUB").uppercase(),
                    summaryFindings = json.optString("summaryFindings", "")
                )
            }
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { e ->
                if (e is CancellationException && e !is TimeoutCancellationException) {
                    throw e
                }
                Log.e(TAG, "Failed to scan document: ${e.message}", e)
                Result.failure(e)
            }
        )
    }
}
