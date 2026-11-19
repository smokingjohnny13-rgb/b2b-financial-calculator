package com.example

import com.example.data.model.AmortizationRow
import com.example.data.model.PaymentFrequency
import com.example.data.model.PnlModel
import com.example.data.model.SavedDeal
import com.example.domain.calculator.CashGapEngine
import com.example.domain.calculator.DealScoringEngine
import com.example.domain.calculator.PlanFactVarianceEngine
import com.example.domain.model.FinancialFormulaGlossary
import com.example.service.CsvExportService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvExportServiceTest {

    @Test
    fun `test csvCell escaping logic`() {
        assertEquals("StandardText", CsvExportService.csvCell("StandardText"))
        assertEquals("\"Contains,Comma\"", CsvExportService.csvCell("Contains,Comma"))
        assertEquals("\"Contains;Semicolon\"", CsvExportService.csvCell("Contains;Semicolon"))
        assertEquals("\"Contains\"\"Quotes\"\"Inside\"", CsvExportService.csvCell("Contains\"Quotes\"Inside"))
        assertEquals("\"Line1\nLine2\"", CsvExportService.csvCell("Line1\nLine2"))
        assertEquals("\"Line1\r\nLine2\"", CsvExportService.csvCell("Line1\r\nLine2"))

        // CSV Formula Injection sanitization
        assertEquals("'=CMD|'/C calc'!A0", CsvExportService.csvCell("=CMD|'/C calc'!A0"))
        assertEquals("\"'=SUM(A1,B1)\"", CsvExportService.csvCell("=SUM(A1,B1)"))
        assertEquals("'+1+1", CsvExportService.csvCell("+1+1"))
        assertEquals("'-1+1", CsvExportService.csvCell("-1+1"))
        assertEquals("'@SUM(1+1)", CsvExportService.csvCell("@SUM(1+1)"))
        assertEquals("\"'\t=HYPERLINK(\"\"http://evil\"\")\"", CsvExportService.csvCell("\t=HYPERLINK(\"http://evil\")"))
        assertEquals("' =1+1", CsvExportService.csvCell(" =1+1"))
    }

    @Test
    fun `test buildActiveDealCsv outputs all 4 boardroom sections`() {
        val deal = SavedDeal(
            id = "DEAL-101",
            title = "Cloud Infrastructure & SRE Agreement",
            clientName = "Acme Retail Global, Inc.",
            currencyCode = "USD",
            dealValue = 250000.0,
            interestRatePct = 12.0,
            tenureMonths = 12,
            frequency = PaymentFrequency.MONTHLY,
            periodicPayment = 22214.50,
            ltv = 750000.0,
            ltvCacRatio = 4.2,
            marginPct = 34.5,
            markupPct = 52.7,
            roiPct = 68.0,
            paybackMonths = 7.2,
            updatedAt = 1762700000000L
        )

        val scoring = DealScoringEngine.calculate(
            sellingPrice = 100.0,
            costPrice = 50.0,
            volumeUnits = 1000,
            onInvoiceDiscountPct = 5.0,
            offInvoiceBonusPct = 6.0,
            paymentTermsDays = 30,
            financingAnnualRatePct = 18.0,
            languageRu = false
        )

        val cashGap = CashGapEngine.calculate(
            dealRevenue = 250000.0,
            cogsTotal = 162500.0,
            prepaidPct = 0.0,
            supplierPayDays = 20,
            customerPayDays = 45,
            inventoryDays = 30,
            overdraftRatePct = 18.0,
            factoringAdvancePct = 80.0,
            factoringFeePct = 1.5,
            factoringInterestRatePct = 15.0
        )

        val pnl = PnlModel(
            grossRevenue = 250000.0,
            cogs = 162500.0,
            operatingExpenses = 25000.0,
            grossProfit = 87500.0,
            grossMarginPct = 35.0,
            ebitda = 62500.0,
            ebitdaMarginPct = 25.0,
            netProfit = 50000.0,
            netMarginPct = 20.0,
            initialInvestment = 50000.0,
            roiPct = 42.5,
            paybackMonths = 6.0,
            fixedCosts = 25000.0,
            variableCostPerUnit = 325.0,
            pricePerUnit = 500.0,
            breakEvenUnits = 450.0,
            breakEvenRevenue = 112500.0
        )

        val amortization = listOf(
            AmortizationRow(1, 22214.50, 19714.50, 2500.0, 230285.50, false),
            AmortizationRow(2, 22214.50, 19911.65, 2302.85, 210373.85, false)
        )

        val csv = CsvExportService.buildActiveDealCsv(deal, scoring, cashGap, pnl, amortization)

        // Verify Header
        assertTrue(csv.contains("NotaBigDeal Commercial Intelligence - Deal Underwriting Model"))
        assertTrue(csv.contains("Contract Title,Cloud Infrastructure & SRE Agreement"))
        assertTrue(csv.contains("Client Account,\"Acme Retail Global, Inc.\""))
        assertTrue(csv.contains("Base Currency,USD"))

        // Verify Section 1: Executive Metrics & Scoring
        assertTrue(csv.contains("--- 1. EXECUTIVE METRICS & DEAL APPROVAL ---"))
        assertTrue(csv.contains("Net-Net Selling Price,89.3,USD,Base List Price: 100.0"))
        assertTrue(csv.contains("Net-Net Margin %,"))
        assertTrue(csv.contains("Cost of Deferral,"))
        assertTrue(csv.contains("Deal Risk Score,"))

        // Verify Section 2: NWC & Factoring Arbitrage (P2)
        assertTrue(csv.contains("--- 2. NET WORKING CAPITAL (NWC) & FACTORING ARBITRAGE (P2) ---"))
        assertTrue(csv.contains("DSO (Days Sales Outstanding),45,Days"))
        assertTrue(csv.contains("DIO (Days Inventory Outstanding),30,Days"))
        assertTrue(csv.contains("DPO (Days Payables Outstanding),20,Days"))
        assertTrue(csv.contains("Cash Conversion Cycle (CCC),55,Days"))
        assertTrue(csv.contains("Factoring Advance Rate,80.0,%"))

        // Verify Section 3: P&L
        assertTrue(csv.contains("--- 3. CONTRACT P&L & BREAK-EVEN ANALYTICS ---"))
        assertTrue(csv.contains("Gross Revenue,250000.0,100.0%"))
        assertTrue(csv.contains("EBITDA,62500.0"))
        assertTrue(csv.contains("Net ROI %,42.5,%"))

        // Verify Section 4: Amortization Schedule
        assertTrue(csv.contains("--- 4. FINANCING AMORTIZATION SCHEDULE ---"))
        assertTrue(csv.contains("1,22214.5,19714.5,2500.0,230285.5,Standard Installment"))
        assertTrue(csv.contains("2,22214.5,19911.65,2302.85,210373.85,Standard Installment"))
    }

    @Test
    fun `test buildVaultRegistryCsv outputs multi-contract table`() {
        val deals = listOf(
            SavedDeal(
                id = "DEAL-001",
                title = "Hardware Supply Contract",
                clientName = "TechCorp Inc.",
                currencyCode = "USD",
                dealValue = 100000.0,
                interestRatePct = 10.0,
                tenureMonths = 24,
                frequency = PaymentFrequency.MONTHLY,
                periodicPayment = 4614.49,
                ltv = 300000.0,
                ltvCacRatio = 5.0,
                marginPct = 25.0,
                markupPct = 33.3,
                roiPct = 40.0,
                paybackMonths = 8.0,
                updatedAt = 1762700000000L
            ),
            SavedDeal(
                id = "DEAL-002",
                title = "Annual Maintenance, SLA & Support",
                clientName = "Global Logistics LLC",
                currencyCode = "EUR",
                dealValue = 150000.0,
                interestRatePct = 8.5,
                tenureMonths = 12,
                frequency = PaymentFrequency.QUARTERLY,
                periodicPayment = 39500.0,
                ltv = 450000.0,
                ltvCacRatio = 6.2,
                marginPct = 45.0,
                markupPct = 81.8,
                roiPct = 75.0,
                paybackMonths = 5.0,
                updatedAt = 1762700000000L
            )
        )

        val csv = CsvExportService.buildVaultRegistryCsv(deals)

        // Header check
        assertTrue(csv.contains("NotaBigDeal - Deal Vault Portfolio Registry"))
        assertTrue(csv.contains("Total Contracts,2"))
        assertTrue(csv.contains("Total Portfolio Value,250000.00"))
        assertTrue(csv.contains("Weighted Average Margin %,35.0%"))

        // Column line check
        assertTrue(csv.contains("Contract ID,Deal Title,Client Account,Currency,Deal Value,Interest Rate %,Tenure (Mo)"))

        // Deal rows
        assertTrue(csv.contains("DEAL-001,Hardware Supply Contract,TechCorp Inc.,USD,100000.00,10.00,24,MONTHLY,4614.49,25.0,33.3,40.0,8.0,300000.00,5.0"))
        assertTrue(csv.contains("DEAL-002,\"Annual Maintenance, SLA & Support\",Global Logistics LLC,EUR,150000.00,8.50,12,QUARTERLY,39500.00,45.0,81.8,75.0,5.0,450000.00,6.2"))
    }

    @Test
    fun `test buildActiveDealCsv includes Plan-Fact Variance Bridge Section 5`() {
        val deal = SavedDeal(
            id = "DEAL-PF-01",
            title = "Enterprise QBR Deal",
            clientName = "Nordic Metals",
            currencyCode = "USD",
            hasPlanFactData = true,
            planFactPlanPrice = 100.0,
            planFactPlanVolume = 1000,
            planFactPlanCost = 60.0,
            planFactPlanDays = 30,
            planFactActualPrice = 96.0,
            planFactActualVolume = 1050,
            planFactActualCost = 63.0,
            planFactActualDays = 48,
            planFactActualPenalties = 500.0,
            planFactCapitalRate = 18.0
        )

        val planFact = PlanFactVarianceEngine.calculate(
            planPrice = deal.planFactPlanPrice,
            planVolume = deal.planFactPlanVolume,
            planCost = deal.planFactPlanCost,
            planDays = deal.planFactPlanDays,
            actualPrice = deal.planFactActualPrice,
            actualVolume = deal.planFactActualVolume,
            actualCost = deal.planFactActualCost,
            actualDays = deal.planFactActualDays,
            actualPenalties = deal.planFactActualPenalties,
            capitalRatePct = deal.planFactCapitalRate
        )

        val csv = CsvExportService.buildActiveDealCsv(
            deal = deal,
            scoring = null,
            cashGap = null,
            pnl = null,
            amortization = emptyList(),
            planFact = planFact
        )

        assertTrue(csv.contains("--- 5. PLAN-FACT VARIANCE BRIDGE ---"))
        assertTrue(csv.contains("1. Volume Effect,-,-,${planFact.volumeEffectProfit},USD"))
        assertTrue(csv.contains("2. Price Effect,-,-,${planFact.priceEffectProfit},USD"))
        assertTrue(csv.contains("3. Cost / COGS Effect,-,-,${planFact.costEffectProfit},USD"))
        assertTrue(csv.contains("4. DSO Deferral Effect,-,-,${planFact.dsoDeferralEffectProfit},USD"))
        assertTrue(csv.contains("5. Penalties Effect,-,-,${planFact.penaltiesEffectProfit},USD"))
        assertTrue(csv.contains("Variance Bridge Status,${planFact.status.name}"))

        // Verify 5-factor bridge identity
        val factorSum = planFact.volumeEffectProfit +
            planFact.priceEffectProfit +
            planFact.costEffectProfit +
            planFact.dsoDeferralEffectProfit +
            planFact.penaltiesEffectProfit
        assertEquals(planFact.netProfitDelta, factorSum, 0.05)
    }

    @Test
    fun `test glossary contains plan_fact_variance formula in EN and RU`() {
        val item = FinancialFormulaGlossary.findById("plan_fact_variance")
        assertNotNull(item)
        assertTrue(item!!.titleEn.contains("Plan-Fact"))
        assertTrue(item.titleRu.contains("План-Факт"))
        assertTrue(item.formulaLatexEn.contains("Volume"))
        assertTrue(item.formulaLatexRu.contains("Штрафы"))
    }
}
