package com.example.service

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.data.model.AmortizationRow
import com.example.data.model.DealScoringResult
import com.example.data.model.PnlModel
import com.example.data.model.SavedDeal
import com.example.domain.calculator.CashGapResult
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Service generating boardroom-grade CSV/Excel compatible spreadsheets.
 * Formatted with UTF-8 BOM (\uFEFF) for seamless opening in Microsoft Excel and Google Sheets.
 */
object CsvExportService {

    /**
     * Exports a comprehensive financial breakdown of the active deal model to CSV/Excel and launches Android Share Sheet.
     */
    fun generateAndShareActiveDealCsv(
        context: Context,
        deal: SavedDeal,
        scoring: DealScoringResult?,
        cashGap: CashGapResult?,
        pnl: PnlModel?,
        amortization: List<AmortizationRow>
    ): Result<File> {
        return runCatching {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()

            val sanitizedTitle = deal.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val csvFile = File(reportsDir, "NotaBigDeal_${sanitizedTitle}_${System.currentTimeMillis()}.csv")

            val csvContent = buildActiveDealCsv(deal, scoring, cashGap, pnl, amortization)
            FileOutputStream(csvFile).use { fos ->
                // Write UTF-8 BOM for Microsoft Excel compatibility
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(csvContent)
                }
            }

            dispatchShareIntent(context, csvFile, "NotaBigDeal Deal Breakdown: ${deal.title} (${deal.clientName})")
            csvFile
        }
    }

    fun buildActiveDealCsv(
        deal: SavedDeal,
        scoring: DealScoringResult?,
        cashGap: CashGapResult?,
        pnl: PnlModel?,
        amortization: List<AmortizationRow>
    ): String {
        val sb = java.lang.StringBuilder()
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val cur = deal.currencyCode

        // Header
        sb.append("NotaBigDeal Commercial Intelligence - Deal Underwriting Model\n")
        sb.append("Export Date,${csvCell(dateStr)}\n")
        sb.append("Contract Title,${csvCell(deal.title)}\n")
        sb.append("Client Account,${csvCell(deal.clientName)}\n")
        sb.append("Base Currency,${csvCell(cur)}\n")
        sb.append("Contact,${csvCell("www.linkedin.com/in/snitkor")}\n\n")

        // Section 1: Executive Deal Metrics & Approval
        sb.append("--- 1. EXECUTIVE METRICS & DEAL APPROVAL ---\n")
        sb.append("Metric,Value,Unit,Details\n")
        if (scoring != null) {
            sb.append("Net-Net Selling Price,${scoring.netNetPrice},$cur,Base List Price: ${scoring.sellingPrice}\n")
            sb.append("Net-Net Margin %,${scoring.netNetMarginPct},%,Gross Margin: ${scoring.grossMarginPct}%\n")
            sb.append("Margin Erosion,${scoring.marginErosionPts},p.p.,Discounts & Rebates deduction\n")
            sb.append("Cost of Deferral,-${scoring.costOfDeferralAmount},$cur,${scoring.paymentTermsDays} days deferral\n")
            sb.append("Deal Risk Score,${scoring.riskScore},Points (0-100),Level: ${scoring.scoringLevel.name}\n")
            sb.append("Net Profit After Capital Cost,${scoring.profitAfterCostOfCapital},$cur,Profit post financing cost\n")
        } else {
            sb.append("Contract Value,${deal.dealValue},$cur,Nominal value\n")
            sb.append("Margin %,${deal.marginPct},%,Gross profit percentage\n")
            sb.append("Markup %,${deal.markupPct},%,Price multiplier over cost\n")
        }
        sb.append("\n")

        // Section 2: Cash Gap, Working Capital (NWC) & Factoring Arbitrage (P2)
        if (cashGap != null) {
            sb.append("--- 2. NET WORKING CAPITAL (NWC) & FACTORING ARBITRAGE (P2) ---\n")
            sb.append("Indicator,Value,Unit,Note\n")
            sb.append("Contract Revenue,${cashGap.dealRevenue},$cur,Total deal turnover\n")
            sb.append("Total COGS,${cashGap.cogsTotal},$cur,Direct cost of goods\n")
            sb.append("DSO (Days Sales Outstanding),${cashGap.customerPayDays},Days,Customer payment deferral\n")
            sb.append("DIO (Days Inventory Outstanding),${cashGap.inventoryDays},Days,Transit & warehouse storage\n")
            sb.append("DPO (Days Payables Outstanding),${cashGap.supplierPayDays},Days,Supplier deferral\n")
            sb.append("Cash Conversion Cycle (CCC),${cashGap.cashConversionCycleDays},Days,Operating cash gap duration\n")
            sb.append("Accounts Receivable (AR),${cashGap.accountsReceivable},$cur,Tied up in unpaid client invoices\n")
            sb.append("Inventory Capital (Inv),${cashGap.inventoryValue},$cur,Tied up in goods/warehouse\n")
            sb.append("Accounts Payable (AP),${cashGap.accountsPayable},$cur,Funded by suppliers\n")
            sb.append("Net Working Capital (NWC),${cashGap.netWorkingCapital},$cur,AR + Inv - AP liquidity need\n")
            sb.append("Factoring Advance Rate,${cashGap.factoringAdvancePct},%,Immediate funding percentage\n")
            sb.append("Immediate Cash Day 0,${cashGap.factoringNetImmediateCash},$cur,Funded on shipment date\n")
            sb.append("Total Factoring Fee & Interest,${cashGap.factoringTotalCost},$cur,Full factoring expense\n")
            sb.append("Effective Factoring APR,${cashGap.factoringAprPct},%,Annualized factoring cost\n")
            sb.append("Required Price Uplift %,+${cashGap.factoringRequiredPriceUpliftPct},%,Compensation needed in price\n")
            sb.append("Alternative Overdraft Cost,${cashGap.overdraftCost},$cur,Bank line expense comparison\n")
            val strategy = if (cashGap.isFactoringCheaper) "Factoring Arbitrage (Cheaper than bank)" else "Bank Overdraft (Cheaper than factor)"
            sb.append("Recommended Capital Strategy,${csvCell(strategy)},-,Underwriting verdict\n\n")
        }

        // Section 3: Contract P&L & Investment Appraisal
        if (pnl != null) {
            sb.append("--- 3. CONTRACT P&L & BREAK-EVEN ANALYTICS ---\n")
            sb.append("Financial Line,Amount ($cur),Percentage (%)\n")
            sb.append("Gross Revenue,${pnl.grossRevenue},100.0%\n")
            sb.append("Cost of Goods Sold (COGS),${pnl.cogs},${if (pnl.grossRevenue > 0) String.format(Locale.US, "%.1f", pnl.cogs / pnl.grossRevenue * 100.0) else "0.0"}%\n")
            sb.append("Gross Profit,${pnl.grossProfit},${if (pnl.grossRevenue > 0) String.format(Locale.US, "%.1f", pnl.grossProfit / pnl.grossRevenue * 100.0) else "0.0"}%\n")
            sb.append("Operating Expenses (OPEX),${pnl.operatingExpenses},${if (pnl.grossRevenue > 0) String.format(Locale.US, "%.1f", pnl.operatingExpenses / pnl.grossRevenue * 100.0) else "0.0"}%\n")
            sb.append("EBITDA,${pnl.ebitda},${if (pnl.grossRevenue > 0) String.format(Locale.US, "%.1f", pnl.ebitda / pnl.grossRevenue * 100.0) else "0.0"}%\n")
            sb.append("Net Profit,${pnl.netProfit},${if (pnl.grossRevenue > 0) String.format(Locale.US, "%.1f", pnl.netProfit / pnl.grossRevenue * 100.0) else "0.0"}%\n")
            sb.append("Net ROI %,${pnl.roiPct},%,Return on invested capital\n")
            sb.append("Break-Even Units,${pnl.breakEvenUnits},Units,Zero-profit sales volume\n")
            sb.append("Break-Even Revenue,${pnl.breakEvenRevenue},$cur,Zero-profit billing volume\n\n")
        }

        // Section 4: Financing & Amortization Schedule
        if (amortization.isNotEmpty()) {
            sb.append("--- 4. FINANCING AMORTIZATION SCHEDULE ---\n")
            sb.append("Period,Installment Payment ($cur),Principal Repaid ($cur),Interest Paid ($cur),Remaining Balance ($cur),Type\n")
            for (item in amortization) {
                val typeLabel = if (item.isGracePeriod) "Grace Period" else "Standard Installment"
                sb.append("${item.period},${item.payment},${item.principal},${item.interest},${item.remainingBalance},$typeLabel\n")
            }
        }
        return sb.toString()
    }

    /**
     * Exports all saved deals in Deal Vault as a structured portfolio table.
     */
    fun generateAndShareVaultRegistryCsv(
        context: Context,
        deals: List<SavedDeal>
    ): Result<File> {
        return runCatching {
            val reportsDir = File(context.cacheDir, "reports")
            if (!reportsDir.exists()) reportsDir.mkdirs()

            val csvFile = File(reportsDir, "NotaBigDeal_Portfolio_Registry_${System.currentTimeMillis()}.csv")

            val csvContent = buildVaultRegistryCsv(deals)
            FileOutputStream(csvFile).use { fos ->
                fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
                    writer.write(csvContent)
                }
            }

            dispatchShareIntent(context, csvFile, "NotaBigDeal Portfolio Registry (${deals.size} Contracts)")
            csvFile
        }
    }

    fun buildVaultRegistryCsv(deals: List<SavedDeal>): String {
        val sb = java.lang.StringBuilder()
        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val exportDate = dateFmt.format(Date())

        val totalValue = deals.sumOf { it.dealValue }
        val avgMargin = if (deals.isNotEmpty()) deals.map { it.marginPct }.average() else 0.0

        // Summary Header
        sb.append("NotaBigDeal - Deal Vault Portfolio Registry\n")
        sb.append("Export Date,${csvCell(exportDate)}\n")
        sb.append("Total Contracts,${deals.size}\n")
        sb.append("Total Portfolio Value,${String.format(Locale.US, "%.2f", totalValue)}\n")
        sb.append("Weighted Average Margin %,${String.format(Locale.US, "%.1f", avgMargin)}%\n")
        sb.append("Contact,${csvCell("www.linkedin.com/in/snitkor")}\n\n")

        // Table Columns
        sb.append("Contract ID,Deal Title,Client Account,Currency,Deal Value,Interest Rate %,Tenure (Mo),Frequency,Periodic Payment,Margin %,Markup %,ROI %,Payback (Mo),LTV,LTV:CAC,Last Updated\n")

        for (deal in deals) {
            val updatedStr = dateFmt.format(Date(deal.updatedAt))
            sb.append(
                listOf(
                    csvCell(deal.id),
                    csvCell(deal.title),
                    csvCell(deal.clientName),
                    csvCell(deal.currencyCode),
                    String.format(Locale.US, "%.2f", deal.dealValue),
                    String.format(Locale.US, "%.2f", deal.interestRatePct),
                    deal.tenureMonths.toString(),
                    csvCell(deal.frequency.name),
                    String.format(Locale.US, "%.2f", deal.periodicPayment),
                    String.format(Locale.US, "%.1f", deal.marginPct),
                    String.format(Locale.US, "%.1f", deal.markupPct),
                    String.format(Locale.US, "%.1f", deal.roiPct),
                    String.format(Locale.US, "%.1f", deal.paybackMonths),
                    String.format(Locale.US, "%.2f", deal.ltv),
                    String.format(Locale.US, "%.1f", deal.ltvCacRatio),
                    csvCell(updatedStr)
                ).joinToString(",") + "\n"
            )
        }
        return sb.toString()
    }

    fun csvCell(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains(";")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    private fun dispatchShareIntent(context: Context, file: File, subject: String) {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(
                Intent.EXTRA_TEXT,
                "Exported from NotaBigDeal — Commercial Intelligence & Underwriting Workstation (connect: www.linkedin.com/in/snitkor)."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, "Share CSV / Excel Spreadsheet")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
