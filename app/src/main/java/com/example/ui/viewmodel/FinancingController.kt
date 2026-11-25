package com.example.ui.viewmodel

import com.example.data.model.AmortizationScheme
import com.example.data.model.AmortizationSummary
import com.example.data.model.PaymentFrequency
import com.example.domain.calculator.AmortizationEngine
import com.example.domain.calculator.CurrencyCreditLegInput
import com.example.domain.calculator.MultiCurrencyCreditEngine
import com.example.domain.calculator.MultiCurrencyCreditResult
import com.example.util.toCleanDouble
import com.example.util.toCleanInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controller for loan amortization schedules, grace periods, early prepayment,
 * and multi-currency credit basket hedging.
 */
class FinancingController(
    private val session: DealSessionState
) {
    val principalAmount = MutableStateFlow("120000")
    val downPayment = MutableStateFlow("15000")
    val annualInterestRate = MutableStateFlow("6.5")
    val tenureMonths = MutableStateFlow("24")
    val paymentFrequency = MutableStateFlow(PaymentFrequency.MONTHLY)

    val isGracePeriodEnabled = MutableStateFlow(false)
    val graceTenureMonths = MutableStateFlow("6")
    val graceInterestRate = MutableStateFlow("0.0")
    val postGraceInterestRate = MutableStateFlow("12.0")
    val customMonthlyPayment = MutableStateFlow("")
    val amortizationScheme = MutableStateFlow(AmortizationScheme.ANNUITY)

    private val _amortization = MutableStateFlow(
        AmortizationEngine.calculate(
            principal = 120000.0,
            annualInterestRatePct = 6.5,
            tenureMonths = 24,
            downPayment = 15000.0,
            frequency = PaymentFrequency.MONTHLY,
            hasGracePeriod = false,
            graceTenureMonths = 6,
            graceInterestRatePct = 0.0,
            postGraceInterestRatePct = 12.0
        )
    )
    val amortization: StateFlow<AmortizationSummary> = _amortization.asStateFlow()

    val creditPrincipal = MutableStateFlow("100000")
    val creditTenureMonths = MutableStateFlow("12")
    val creditSelectedCurrencies = MutableStateFlow(listOf("USD", "EUR", "RUB", "CNY"))
    val creditHedgeEnabled = MutableStateFlow(true)
    val creditHedgeCostPct = MutableStateFlow("2.5")
    val creditExpectedFxShiftPct = MutableStateFlow("8.0")
    val creditCurrencyRates = MutableStateFlow(
        mapOf(
            "USD" to 7.5,
            "EUR" to 5.5,
            "RUB" to 18.5,
            "CNY" to 4.2,
            "BYN" to 11.5,
            "KZT" to 14.5,
            "AED" to 6.0,
            "TRY" to 42.0
        )
    )
    val creditAllocations = MutableStateFlow(
        mapOf(
            "USD" to 25.0,
            "EUR" to 25.0,
            "RUB" to 25.0,
            "CNY" to 25.0
        )
    )

    private val _multiCreditResult = MutableStateFlow(
        MultiCurrencyCreditEngine.calculate(
            dealAmount = 100000.0,
            baseCurrencyCode = "USD",
            tenureMonths = 12,
            legsInput = listOf(
                CurrencyCreditLegInput("USD", 7.5, 25.0, 2.5, 8.0, true),
                CurrencyCreditLegInput("EUR", 5.5, 25.0, 2.5, 8.0, true),
                CurrencyCreditLegInput("RUB", 18.5, 25.0, 2.5, 8.0, true),
                CurrencyCreditLegInput("CNY", 4.2, 25.0, 2.5, 8.0, true)
            )
        )
    )
    val multiCreditResult: StateFlow<MultiCurrencyCreditResult> = _multiCreditResult.asStateFlow()

    fun updateFinancing() {
        val p = principalAmount.value.toCleanDouble() ?: 0.0
        val down = downPayment.value.toCleanDouble() ?: 0.0
        val r = annualInterestRate.value.toCleanDouble() ?: 0.0
        val tenure = tenureMonths.value.toCleanInt() ?: 12
        val hasGrace = isGracePeriodEnabled.value
        val graceTenure = graceTenureMonths.value.toCleanInt() ?: 0
        val graceRate = graceInterestRate.value.toCleanDouble() ?: 0.0
        val postGraceRate = postGraceInterestRate.value.toCleanDouble() ?: r
        val customPayment = customMonthlyPayment.value.toCleanDouble()

        _amortization.value = AmortizationEngine.calculate(
            principal = p,
            annualInterestRatePct = r,
            tenureMonths = tenure,
            downPayment = down,
            frequency = paymentFrequency.value,
            hasGracePeriod = hasGrace,
            graceTenureMonths = graceTenure,
            graceInterestRatePct = graceRate,
            postGraceInterestRatePct = postGraceRate,
            scheme = amortizationScheme.value,
            customPeriodicPayment = customPayment
        )
        session.updateCurrencyConversion(p)
    }

    fun setAmortizationScheme(scheme: AmortizationScheme) {
        amortizationScheme.value = scheme
        updateFinancing()
    }

    fun setCustomMonthlyPayment(value: String) {
        customMonthlyPayment.value = value
        updateFinancing()
    }

    fun clearCustomMonthlyPayment() {
        customMonthlyPayment.value = ""
        updateFinancing()
    }

    fun toggleGracePeriod(enabled: Boolean) {
        isGracePeriodEnabled.value = enabled
        if (enabled && (postGraceInterestRate.value.isEmpty() || postGraceInterestRate.value == "0.0")) {
            postGraceInterestRate.value = annualInterestRate.value
        }
        updateFinancing()
    }

    fun setPaymentFrequency(freq: PaymentFrequency) {
        paymentFrequency.value = freq
        updateFinancing()
    }

    fun toggleCreditCurrency(code: String) {
        val current = creditSelectedCurrencies.value.toMutableList()
        if (current.contains(code)) {
            if (current.size > 1) {
                current.remove(code)
            }
        } else {
            if (current.size < 4) {
                current.add(code)
            }
        }
        creditSelectedCurrencies.value = current
        rebalanceCreditAllocations()
        updateMultiCreditCalculations()
    }

    fun setCreditCurrenciesCount(count: Int) {
        val allAvailable = listOf("USD", "EUR", "CNY", "RUB", "BYN", "KZT")
        creditSelectedCurrencies.value = allAvailable.take(count.coerceIn(1, 4))
        rebalanceCreditAllocations()
        updateMultiCreditCalculations()
    }

    private fun rebalanceCreditAllocations() {
        val currs = creditSelectedCurrencies.value
        val equalShare = 100.0 / currs.size
        val newAlloc = mutableMapOf<String, Double>()
        currs.forEach { c -> newAlloc[c] = equalShare }
        creditAllocations.value = newAlloc
    }

    fun updateCreditCurrencyRate(code: String, rate: Double) {
        val current = creditCurrencyRates.value.toMutableMap()
        current[code] = rate
        creditCurrencyRates.value = current
        updateMultiCreditCalculations()
    }

    fun updateCreditAllocation(code: String, alloc: Double) {
        val current = creditAllocations.value.toMutableMap()
        current[code] = alloc
        creditAllocations.value = current
        updateMultiCreditCalculations()
    }

    fun setCreditHedgeEnabled(enabled: Boolean) {
        creditHedgeEnabled.value = enabled
        updateMultiCreditCalculations()
    }

    fun updateMultiCreditCalculations() {
        val amt = creditPrincipal.value.toCleanDouble() ?: 100000.0
        val tenure = creditTenureMonths.value.toCleanInt() ?: 12
        val hedgeRate = creditHedgeCostPct.value.toCleanDouble() ?: 2.5
        val fxShift = creditExpectedFxShiftPct.value.toCleanDouble() ?: 8.0
        val isHedged = creditHedgeEnabled.value
        val currs = creditSelectedCurrencies.value

        val legs = currs.map { c ->
            val rate = creditCurrencyRates.value[c] ?: MultiCurrencyCreditEngine.getDefaultRate(c)
            val alloc = creditAllocations.value[c] ?: (100.0 / currs.size)
            CurrencyCreditLegInput(
                currencyCode = c,
                annualRatePct = rate,
                allocationPct = alloc,
                hedgeCostAnnualPct = hedgeRate,
                expectedFxShiftPct = fxShift,
                isHedged = isHedged
            )
        }

        _multiCreditResult.value = MultiCurrencyCreditEngine.calculate(
            dealAmount = amt,
            baseCurrencyCode = session.activeCurrency.value.code,
            tenureMonths = tenure,
            legsInput = legs,
            rates = session.currencyRates.value
        )
    }
}
