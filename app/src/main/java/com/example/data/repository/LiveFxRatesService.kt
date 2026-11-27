package com.example.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL

enum class FxCountryRegion(
    val code: String,
    val shortEn: String,
    val shortRu: String,
    val defaultBenchmarkCurrency: String
) {
    ALL("ALL", "All Markets", "Все страны", "EUR"),
    BY("BY", "Belarus", "Беларусь", "BYN"),
    RU("RU", "Russia", "Россия", "RUB"),
    KZ("KZ", "Kazakhstan", "Казахстан", "KZT"),
    GLOBAL("GLOBAL", "EU / Global", "ЕС / Мир", "EUR")
}

enum class FxRateMode {
    MARKET_BANK,
    CENTRAL_BANK
}

data class FxRateSourcePoint(
    val id: String,
    val country: FxCountryRegion,
    val providerNameEn: String,
    val providerNameRu: String,
    val pointDetailsEn: String,
    val pointDetailsRu: String,
    val ratesToUsd: Map<String, Double>,
    val fetchedAtMillis: Long,
    val isCentralBank: Boolean = false
) {
    fun crossRate(baseCode: String, targetCode: String): Double? {
        val baseKey = baseCode.uppercase()
        val targetKey = targetCode.uppercase()
        if (baseKey == targetKey) return 1.0
        val baseRate = ratesToUsd[baseKey] ?: return null
        val targetRate = ratesToUsd[targetKey] ?: return null
        if (!baseRate.isFinite() || !targetRate.isFinite() || baseRate <= 0.0 || targetRate <= 0.0) return null
        return targetRate / baseRate
    }
}

data class FxFetchOutcome(
    val region: FxCountryRegion,
    val points: List<FxRateSourcePoint>,
    val fetchedAtMillis: Long,
    val succeededProviders: Int,
    val failedProviders: Int
)

class LiveFxRatesService(
    private val httpGet: suspend (String) -> String? = ::defaultHttpsGet
) {

    suspend fun fetchRatesForCountry(
        region: FxCountryRegion,
        useClientCountry: Boolean = true
    ): FxFetchOutcome = coroutineScope {
        val now = System.currentTimeMillis()
        val jobs = if (!useClientCountry) {
            listOf(
                async { fetchOpenErApi(now) },
                async { fetchFrankfurter(now) },
                async { fetchJsDelivrUsd(now) }
            )
        } else {
            when (region) {
                FxCountryRegion.BY -> listOf(
                    async { fetchNbrb(now) },
                    async { fetchBelarusbank(now) },
                    async { fetchOpenErApi(now) }
                )
                FxCountryRegion.RU -> listOf(
                    async { fetchCbrDaily(now) },
                    async { fetchOpenErApi(now) },
                    async { fetchJsDelivrUsd(now) }
                )
                FxCountryRegion.KZ -> listOf(
                    async { fetchNbkXml(now) },
                    async { fetchOpenErApi(now) },
                    async { fetchJsDelivrUsd(now) }
                )
                FxCountryRegion.GLOBAL -> listOf(
                    async { fetchFrankfurter(now) },
                    async { fetchOpenErApi(now) },
                    async { fetchJsDelivrUsd(now) }
                )
                FxCountryRegion.ALL -> listOf(
                    async { fetchOpenErApi(now) },
                    async { fetchFrankfurter(now) },
                    async { fetchNbrb(now) },
                    async { fetchBelarusbank(now) },
                    async { fetchCbrDaily(now) },
                    async { fetchNbkXml(now) },
                    async { fetchJsDelivrUsd(now) }
                )
            }
        }

        val providerResults = jobs.awaitAll()
        val succeeded = providerResults.count { it.isNotEmpty() }
        val failed = providerResults.size - succeeded
        FxFetchOutcome(
            region = region,
            points = providerResults.flatten(),
            fetchedAtMillis = now,
            succeededProviders = succeeded,
            failedProviders = failed
        )
    }

    private suspend fun fetchOpenErApi(now: Long): List<FxRateSourcePoint> {
        val body = httpGet("https://open.er-api.com/v6/latest/USD") ?: return emptyList()
        return listOfNotNull(parseOpenErApi(body, now))
    }

    private suspend fun fetchJsDelivrUsd(now: Long): List<FxRateSourcePoint> {
        val body = httpGet("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json")
            ?: httpGet("https://latest.currency-api.pages.dev/v1/currencies/usd.json")
            ?: return emptyList()
        return listOfNotNull(parseJsDelivrUsd(body, now))
    }

    private suspend fun fetchFrankfurter(now: Long): List<FxRateSourcePoint> {
        val body = httpGet("https://api.frankfurter.app/latest?from=USD") ?: return emptyList()
        return listOfNotNull(parseFrankfurter(body, now))
    }

    private suspend fun fetchNbrb(now: Long): List<FxRateSourcePoint> {
        val body = httpGet("https://api.nbrb.by/exrates/rates?periodicity=0") ?: return emptyList()
        return listOfNotNull(parseNbrb(body, now))
    }

    private suspend fun fetchBelarusbank(now: Long): List<FxRateSourcePoint> {
        val body = httpGet("https://belarusbank.by/api/kursExchange?city=%D0%9C%D0%B8%D0%BD%D1%81%D0%BA")
            ?: return emptyList()
        return parseBelarusbankBranches(body, now)
    }

    private suspend fun fetchCbrDaily(now: Long): List<FxRateSourcePoint> {
        val body = httpGet("https://www.cbr-xml-daily.ru/daily_json.js") ?: return emptyList()
        return listOfNotNull(parseCbrDaily(body, now))
    }

    private suspend fun fetchNbkXml(now: Long): List<FxRateSourcePoint> {
        val body = httpGet("https://nationalbank.kz/rss/rates_all.xml") ?: return emptyList()
        return listOfNotNull(parseNbkXml(body, now))
    }

    companion object {

        fun findCentralBankPoint(
            points: List<FxRateSourcePoint>,
            region: FxCountryRegion
        ): FxRateSourcePoint? {
            val centralBanks = points.filter { it.isCentralBank }
            if (centralBanks.isEmpty()) return null
            if (region == FxCountryRegion.ALL) return centralBanks.first()
            return centralBanks.firstOrNull { it.country == region } ?: centralBanks.first()
        }

        fun rankPointsForPair(
            points: List<FxRateSourcePoint>,
            baseCode: String,
            targetCode: String,
            region: FxCountryRegion,
            mode: FxRateMode = FxRateMode.MARKET_BANK,
            useClientCountry: Boolean = false
        ): List<FxRateSourcePoint> {
            if (points.isEmpty()) return emptyList()
            val effectiveTarget = if (baseCode.equals(targetCode, ignoreCase = true)) {
                if (baseCode.equals("USD", ignoreCase = true)) region.defaultBenchmarkCurrency else "USD"
            } else {
                targetCode
            }

            fun sortByQuote(list: List<FxRateSourcePoint>): List<FxRateSourcePoint> {
                val withQuote = list.mapNotNull { p ->
                    val quote = p.crossRate(baseCode, effectiveTarget)
                    if (quote != null && quote.isFinite() && quote > 0.0) p to quote else null
                }
                val sorted = withQuote
                    .sortedWith(
                        compareByDescending<Pair<FxRateSourcePoint, Double>> { it.second }
                            .thenByDescending { it.first.ratesToUsd.size }
                    )
                    .map { it.first }
                val missing = list.filter { p -> withQuote.none { it.first.id == p.id } }
                return sorted + missing
            }

            val preferredPool = if (useClientCountry) {
                when (mode) {
                    FxRateMode.CENTRAL_BANK -> {
                        val cb = points.filter { it.isCentralBank && (region == FxCountryRegion.ALL || it.country == region) }
                        cb.ifEmpty { points.filter { it.isCentralBank } }
                    }
                    FxRateMode.MARKET_BANK -> points.filter { !it.isCentralBank }
                }
            } else {
                points.filter { !it.isCentralBank }
            }

            val prioritized = sortByQuote(preferredPool.ifEmpty { points })
            val remainder = sortByQuote(points.filter { p -> prioritized.none { it.id == p.id } })
            return prioritized + remainder
        }

        fun parseOpenErApi(jsonText: String, now: Long): FxRateSourcePoint? {
            return try {
                val root = JSONObject(jsonText)
                val ratesObj = root.optJSONObject("rates") ?: return null
                val map = mutableMapOf("USD" to 1.0)
                val keys = ratesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next().uppercase()
                    val v = ratesObj.optDouble(key, Double.NaN)
                    if (v.isFinite() && v > 0.0) map[key] = v
                }
                if (map.size < 3) return null
                FxRateSourcePoint(
                    id = "open_er_api",
                    country = FxCountryRegion.GLOBAL,
                    providerNameEn = "Market Spot (Open ER)",
                    providerNameRu = "Рыночный Spot (Open ER)",
                    pointDetailsEn = "Global Interbank Mid-Market",
                    pointDetailsRu = "Мировой межбанковский курс",
                    ratesToUsd = map,
                    fetchedAtMillis = now
                )
            } catch (_: Exception) {
                null
            }
        }

        fun parseJsDelivrUsd(jsonText: String, now: Long): FxRateSourcePoint? {
            return try {
                val root = JSONObject(jsonText)
                val usdObj = root.optJSONObject("usd") ?: return null
                val map = mutableMapOf("USD" to 1.0)
                val keys = usdObj.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    val code = rawKey.uppercase()
                    val v = usdObj.optDouble(rawKey, Double.NaN)
                    if (code.length in 3..5 && v.isFinite() && v > 0.0) {
                        map[code] = v
                    }
                }
                if (map.size < 3) return null
                FxRateSourcePoint(
                    id = "currency_api_cdn",
                    country = FxCountryRegion.GLOBAL,
                    providerNameEn = "Global FX CDN",
                    providerNameRu = "Глобальный FX агрегатор",
                    pointDetailsEn = "Multi-feed Currency CDN",
                    pointDetailsRu = "Мульти-агрегатор курсов CDN",
                    ratesToUsd = map,
                    fetchedAtMillis = now
                )
            } catch (_: Exception) {
                null
            }
        }

        fun parseFrankfurter(jsonText: String, now: Long): FxRateSourcePoint? {
            return try {
                val root = JSONObject(jsonText)
                val ratesObj = root.optJSONObject("rates") ?: return null
                val map = mutableMapOf("USD" to 1.0)
                val keys = ratesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next().uppercase()
                    val v = ratesObj.optDouble(key, Double.NaN)
                    if (v.isFinite() && v > 0.0) map[key] = v
                }
                if (map.size < 2) return null
                FxRateSourcePoint(
                    id = "ecb_frankfurter",
                    country = FxCountryRegion.GLOBAL,
                    providerNameEn = "ECB (European Central Bank)",
                    providerNameRu = "ЕЦБ (Европейский центробанк)",
                    pointDetailsEn = "Official Eurosystem Reference",
                    pointDetailsRu = "Официальный курс ЕЦБ",
                    ratesToUsd = map,
                    fetchedAtMillis = now,
                    isCentralBank = true
                )
            } catch (_: Exception) {
                null
            }
        }

        fun parseNbrb(jsonText: String, now: Long): FxRateSourcePoint? {
            return try {
                val arr = JSONArray(jsonText)
                val bynPerUnit = mutableMapOf<String, Double>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val abbr = obj.optString("Cur_Abbreviation").uppercase()
                    val scale = obj.optDouble("Cur_Scale", 1.0)
                    val official = obj.optDouble("Cur_OfficialRate", Double.NaN)
                    if (abbr.isNotBlank() && scale > 0.0 && official.isFinite() && official > 0.0) {
                        bynPerUnit[abbr] = official / scale
                    }
                }
                val bynPerUsd = bynPerUnit["USD"] ?: return null
                if (bynPerUsd <= 0.0) return null

                val map = mutableMapOf("USD" to 1.0, "BYN" to bynPerUsd)
                for ((code, rateInByn) in bynPerUnit) {
                    if (code != "USD" && rateInByn > 0.0) {
                        map[code] = bynPerUsd / rateInByn
                    }
                }
                FxRateSourcePoint(
                    id = "nbrb_official",
                    country = FxCountryRegion.BY,
                    providerNameEn = "NBRB (Belarus Official)",
                    providerNameRu = "НБРБ (Нацбанк Беларуси)",
                    pointDetailsEn = "Official National Bank Rate",
                    pointDetailsRu = "Официальный курс Нацбанка РБ",
                    ratesToUsd = map,
                    fetchedAtMillis = now,
                    isCentralBank = true
                )
            } catch (_: Exception) {
                null
            }
        }

        fun parseBelarusbankBranches(jsonText: String, now: Long, maxBranches: Int = 5): List<FxRateSourcePoint> {
            return try {
                val arr = JSONArray(jsonText)
                val seenSignature = mutableSetOf<String>()
                val points = mutableListOf<FxRateSourcePoint>()
                val branchScores = mutableListOf<Triple<String, Double, Double>>()

                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val usdIn = obj.optString("USD_in").toDoubleOrNull() ?: continue
                    val usdOut = obj.optString("USD_out").toDoubleOrNull() ?: continue
                    if (usdIn <= 0.0 || usdOut <= 0.0) continue

                    val usdMid = (usdIn + usdOut) / 2.0
                    val sig = "%.4f_%.4f".format(java.util.Locale.US, usdIn, usdOut)
                    if (!seenSignature.add(sig)) continue

                    val map = mutableMapOf("USD" to 1.0, "BYN" to usdMid)

                    fun addScaledMid(code: String, inKey: String, outKey: String, scaleThreshold: Double, scaleDivisor: Double) {
                        val vIn = obj.optString(inKey).toDoubleOrNull()
                        val vOut = obj.optString(outKey).toDoubleOrNull()
                        if (vIn != null && vOut != null && vIn > 0.0 && vOut > 0.0) {
                            val rawMid = (vIn + vOut) / 2.0
                            val perOne = if (rawMid > scaleThreshold) rawMid / scaleDivisor else rawMid
                            if (perOne > 0.0) {
                                map[code] = usdMid / perOne
                            }
                        }
                    }

                    addScaledMid("EUR", "EUR_in", "EUR_out", scaleThreshold = 100.0, scaleDivisor = 1.0)
                    addScaledMid("RUB", "RUB_in", "RUB_out", scaleThreshold = 0.5, scaleDivisor = 100.0)
                    addScaledMid("CNY", "CNY_in", "CNY_out", scaleThreshold = 1.5, scaleDivisor = 10.0)
                    addScaledMid("PLN", "PLN_in", "PLN_out", scaleThreshold = 2.0, scaleDivisor = 10.0)
                    addScaledMid("GBP", "GBP_in", "GBP_out", scaleThreshold = 100.0, scaleDivisor = 1.0)

                    val branchTitle = obj.optString("filials_text").trim().ifEmpty { "Отделение" }
                    val city = obj.optString("name").trim()
                    val street = obj.optString("street").trim()
                    val house = obj.optString("home_number").trim()
                    val address = listOf(city, listOf(street, house).filter { it.isNotEmpty() }.joinToString(" "))
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")

                    val details = buildString {
                        if (address.isNotEmpty()) append(address).append(" · ")
                        append("USD ${"%.3f".format(java.util.Locale.US, usdIn)}/${"%.3f".format(java.util.Locale.US, usdOut)}")
                    }

                    val usdBuyScore = usdIn
                    val usdSellScore = usdOut
                    points.add(
                        FxRateSourcePoint(
                            id = "belarusbank_${points.size}_$sig",
                            country = FxCountryRegion.BY,
                            providerNameEn = "Belarusbank · $branchTitle",
                            providerNameRu = "Беларусбанк · $branchTitle",
                            pointDetailsEn = details,
                            pointDetailsRu = details,
                            ratesToUsd = map,
                            fetchedAtMillis = now
                        )
                    )
                    branchScores.add(Triple(points.last().id, usdBuyScore, usdSellScore))
                }
                val topBuyIds = branchScores.sortedByDescending { it.second }.take((maxBranches + 1) / 2).map { it.first }
                val topSellIds = branchScores.sortedBy { it.third }.take((maxBranches + 1) / 2).map { it.first }
                val keepIds = (topBuyIds + topSellIds).toSet()
                points.filter { it.id in keepIds }.take(maxBranches)
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun parseCbrDaily(jsonText: String, now: Long): FxRateSourcePoint? {
            return try {
                val root = JSONObject(jsonText)
                val valute = root.optJSONObject("Valute") ?: return null
                val rubPerUnit = mutableMapOf<String, Double>()
                val keys = valute.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = valute.optJSONObject(key) ?: continue
                    val charCode = obj.optString("CharCode", key).uppercase()
                    val nominal = obj.optDouble("Nominal", 1.0)
                    val value = obj.optDouble("Value", Double.NaN)
                    if (charCode.isNotBlank() && nominal > 0.0 && value.isFinite() && value > 0.0) {
                        rubPerUnit[charCode] = value / nominal
                    }
                }
                val rubPerUsd = rubPerUnit["USD"] ?: return null
                if (rubPerUsd <= 0.0) return null

                val map = mutableMapOf("USD" to 1.0, "RUB" to rubPerUsd)
                for ((code, rateInRub) in rubPerUnit) {
                    if (code != "USD" && rateInRub > 0.0) {
                        map[code] = rubPerUsd / rateInRub
                    }
                }
                FxRateSourcePoint(
                    id = "cbr_official",
                    country = FxCountryRegion.RU,
                    providerNameEn = "CBR (Bank of Russia)",
                    providerNameRu = "ЦБ РФ (Банк России)",
                    pointDetailsEn = "Official Central Bank of Russia",
                    pointDetailsRu = "Официальный курс ЦБ РФ",
                    ratesToUsd = map,
                    fetchedAtMillis = now,
                    isCentralBank = true
                )
            } catch (_: Exception) {
                null
            }
        }

        fun parseNbkXml(xmlText: String, now: Long): FxRateSourcePoint? {
            return try {
                val factory = XmlPullParserFactory.newInstance()
                val parser = factory.newPullParser()
                parser.setInput(StringReader(xmlText))

                val kztPerUnit = mutableMapOf<String, Double>()
                var inItem = false
                var currentTag = ""
                var title = ""
                var description = ""
                var quant = ""

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            currentTag = parser.name?.lowercase() ?: ""
                            if (currentTag == "item") {
                                inItem = true
                                title = ""
                                description = ""
                                quant = ""
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inItem) {
                                when (currentTag) {
                                    "title" -> title += parser.text
                                    "description" -> description += parser.text
                                    "quant" -> quant += parser.text
                                }
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            val endTag = parser.name?.lowercase() ?: ""
                            if (endTag == "item" && inItem) {
                                val code = title.trim().uppercase()
                                val descVal = description.trim().replace(',', '.').toDoubleOrNull()
                                val quantVal = quant.trim().replace(',', '.').toDoubleOrNull() ?: 1.0
                                if (code.length in 3..5 && descVal != null && descVal > 0.0 && quantVal > 0.0) {
                                    kztPerUnit[code] = descVal / quantVal
                                }
                                inItem = false
                            }
                            currentTag = ""
                        }
                    }
                    eventType = parser.next()
                }

                val kztPerUsd = kztPerUnit["USD"] ?: return null
                if (kztPerUsd <= 0.0) return null

                val map = mutableMapOf("USD" to 1.0, "KZT" to kztPerUsd)
                for ((code, rateInKzt) in kztPerUnit) {
                    if (code != "USD" && rateInKzt > 0.0) {
                        map[code] = kztPerUsd / rateInKzt
                    }
                }
                FxRateSourcePoint(
                    id = "nbk_official",
                    country = FxCountryRegion.KZ,
                    providerNameEn = "NBK (Kazakhstan Official)",
                    providerNameRu = "НБ РК (Нацбанк Казахстана)",
                    pointDetailsEn = "Official National Bank of Kazakhstan",
                    pointDetailsRu = "Официальный курс Нацбанка РК",
                    ratesToUsd = map,
                    fetchedAtMillis = now,
                    isCentralBank = true
                )
            } catch (_: Exception) {
                null
            }
        }

        suspend fun defaultHttpsGet(urlString: String): String? = withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 7000
                    setRequestProperty("Accept", "application/json, application/xml, text/xml")
                    setRequestProperty("User-Agent", "NotaBigDeal/1.4 Android")
                }
                val code = connection.responseCode
                if (code !in 200..299) return@withContext null
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
    }
}
