package com.example.ui.viewmodel

import com.example.data.model.CurrencyRate
import com.example.domain.calculator.CurrencyEngine
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

/**
 * Shared session container for the active deal context, currency conversion,
 * localization/theme preferences, and user toast notifications.
 */
class DealSessionState(
    private val scope: CoroutineScope
) {
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    val dealTitle = MutableStateFlow("Enterprise Platform & Support Agreement")
    val clientName = MutableStateFlow("Globex International")
    val selectedCurrency = MutableStateFlow("USD")

    val appLanguage = MutableStateFlow(AppLanguage.EN)
    val isDarkTheme = MutableStateFlow(true)
    val isPrivacyModeEnabled = MutableStateFlow(false)

    val activeCurrency = MutableStateFlow(CurrencyEngine.findByCode("USD"))
    val customCurrencies = MutableStateFlow(CurrencyEngine.DEFAULT_CURRENCIES)
    val currencyRates = MutableStateFlow(CurrencyEngine.DEFAULT_CURRENCIES)
    val targetCurrency = MutableStateFlow("EUR")
    val convertedDealValue = MutableStateFlow(CurrencyEngine.convert(120000.0, "USD", "EUR"))
    val currentCrossRate = MutableStateFlow(CurrencyEngine.getCrossRate("USD", "EUR"))

    private val currencyNumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    fun emitMessage(message: String) {
        scope.launch {
            _userMessage.emit(message)
        }
    }

    fun emitLocalized(en: String, ru: String) {
        emitMessage(str(en, ru, appLanguage.value))
    }

    fun toggleTheme() {
        isDarkTheme.value = !isDarkTheme.value
        if (isDarkTheme.value) {
            emitLocalized("Obsidian Executive Dark Theme enabled", "Включена обсидиановая темная тема")
        } else {
            emitLocalized("Ergonomic Nordic Light Theme enabled", "Включена эргономичная светлая тема")
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        isPrivacyModeEnabled.value = enabled
        if (enabled) {
            emitLocalized(
                "Screen Privacy enabled (screenshots & previews blocked)",
                "Защита экрана включена (скриншоты и превью заблокированы)"
            )
        } else {
            emitLocalized("Screen Privacy disabled", "Защита экрана отключена")
        }
    }

    fun setTargetCurrency(code: String, currentPrincipalAmount: Double) {
        targetCurrency.value = code
        updateCurrencyConversion(currentPrincipalAmount)
    }

    fun updateCurrencyConversion(currentPrincipalAmount: Double) {
        convertedDealValue.value = CurrencyEngine.convert(
            amount = currentPrincipalAmount,
            sourceCode = selectedCurrency.value,
            targetCode = targetCurrency.value,
            rates = currencyRates.value
        )
        currentCrossRate.value = CurrencyEngine.getCrossRate(
            sourceCode = selectedCurrency.value,
            targetCode = targetCurrency.value,
            rates = currencyRates.value
        )
    }

    fun selectActiveCurrency(code: String, currentPrincipalAmount: Double) {
        selectedCurrency.value = code
        val found = CurrencyEngine.findByCode(code, customCurrencies.value)
        activeCurrency.value = found
        updateCurrencyConversion(currentPrincipalAmount)
        emitLocalized(
            "Currency changed: ${found.code} (${found.symbol})",
            "Валюта изменена: ${found.code} (${found.symbol})"
        )
    }

    fun addCustomCurrency(
        code: String,
        name: String,
        symbol: String,
        rateToBaseUsd: Double,
        currentPrincipalAmount: Double
    ) {
        val safeCode = code.uppercase().trim().ifEmpty { "CUR" }
        val safeName = name.trim().ifEmpty { safeCode }
        val safeSym = symbol.trim().ifEmpty { safeCode }
        val safeRate = if (rateToBaseUsd.isFinite() && rateToBaseUsd > 0.0) rateToBaseUsd else 1.0
        val newRate = CurrencyRate(safeCode, safeName, safeSym, safeRate)
        val list = customCurrencies.value.filter { !it.code.equals(newRate.code, ignoreCase = true) } + newRate
        customCurrencies.value = list
        currencyRates.value = list
        selectActiveCurrency(newRate.code, currentPrincipalAmount)
    }

    fun syncCurrencyIfKnown(code: String) {
        val matched = customCurrencies.value.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: return
        selectedCurrency.value = matched.code
        activeCurrency.value = matched
    }

    fun formatCurrency(amount: Double): String {
        val sym = activeCurrency.value.symbol
        val isNegative = amount < 0
        val absAmount = kotlin.math.abs(amount)
        val formattedNum = synchronized(currencyNumberFormat) {
            currencyNumberFormat.format(absAmount)
        }
        val sign = if (isNegative) "-" else ""
        return if (sym == "$" || sym == "€" || sym == "£" || sym == "¥" || sym == "₽") {
            "$sign$sym$formattedNum"
        } else {
            "$sign$sym $formattedNum"
        }
    }

    fun formatCleanNum(value: Double): String {
        if (!value.isFinite()) return "0"
        return if (value % 1.0 == 0.0 && value >= Long.MIN_VALUE && value <= Long.MAX_VALUE) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.2f", value)
        }
    }

    fun resetSessionMetadata() {
        dealTitle.value = "Enterprise Platform & Support Agreement"
        clientName.value = "Globex International"
    }
}
