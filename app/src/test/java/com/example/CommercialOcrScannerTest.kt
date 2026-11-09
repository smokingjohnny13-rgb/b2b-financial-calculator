package com.example

import com.example.data.ai.OcrExtractedDealData
import com.example.domain.calculator.DealScoringEngine
import com.example.domain.calculator.PriceWaterfallEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialOcrScannerTest {

    @Test
    fun `test OcrExtractedDealData default values`() {
        val data = OcrExtractedDealData()
        assertEquals("", data.clientName)
        assertEquals("", data.documentType)
        assertNull(data.shelfPriceRsp)
        assertEquals(20.0, data.vatRatePct ?: 0.0, 0.01)
        assertNull(data.retailerMarginPct)
        assertNull(data.frontDiscountPct)
        assertNull(data.backBonusPct)
        assertNull(data.logisticsCostPerUnit)
        assertNull(data.supplierCogs)
        assertNull(data.paymentTermsDays)
        assertNull(data.contractVolumeUnits)
        assertEquals("RUB", data.currencyCode)
        assertEquals("", data.summaryFindings)
    }

    @Test
    fun `test OcrExtractedDealData full properties construction and validation`() {
        val extracted = OcrExtractedDealData(
            clientName = "Magnit Retail Chain",
            documentType = "Specification Annex #4",
            shelfPriceRsp = 249.90,
            vatRatePct = 20.0,
            retailerMarginPct = 28.5,
            frontDiscountPct = 6.0,
            backBonusPct = 8.5,
            logisticsCostPerUnit = 5.20,
            supplierCogs = 110.0,
            paymentTermsDays = 45,
            contractVolumeUnits = 12000,
            currencyCode = "RUB",
            summaryFindings = "Extracted 12,000 units spec with 28.5% chain margin and 45 days deferral."
        )

        assertEquals("Magnit Retail Chain", extracted.clientName)
        assertEquals("Specification Annex #4", extracted.documentType)
        assertEquals(249.90, extracted.shelfPriceRsp ?: 0.0, 0.01)
        assertEquals(20.0, extracted.vatRatePct ?: 0.0, 0.01)
        assertEquals(28.5, extracted.retailerMarginPct ?: 0.0, 0.01)
        assertEquals(6.0, extracted.frontDiscountPct ?: 0.0, 0.01)
        assertEquals(8.5, extracted.backBonusPct ?: 0.0, 0.01)
        assertEquals(5.20, extracted.logisticsCostPerUnit ?: 0.0, 0.01)
        assertEquals(110.0, extracted.supplierCogs ?: 0.0, 0.01)
        assertEquals(45, extracted.paymentTermsDays)
        assertEquals(12000, extracted.contractVolumeUnits)
        assertEquals("RUB", extracted.currencyCode)
        assertTrue(extracted.summaryFindings.contains("12,000 units"))
    }

    @Test
    fun `test OcrExtractedDealData sparse optional fields`() {
        val extracted = OcrExtractedDealData(
            clientName = "X5 Corporate",
            documentType = "Price List",
            shelfPriceRsp = 150.0,
            currencyCode = "RUB"
        )

        assertEquals("X5 Corporate", extracted.clientName)
        assertEquals(150.0, extracted.shelfPriceRsp ?: 0.0, 0.01)
        assertNull(extracted.frontDiscountPct)
        assertNull(extracted.backBonusPct)
        assertNull(extracted.retailerMarginPct)
        assertNull(extracted.supplierCogs)
        assertNull(extracted.paymentTermsDays)
        assertNull(extracted.contractVolumeUnits)
    }

    @Test
    fun `test applying extracted OCR parameters to Price Waterfall calculations`() {
        // Given extracted OCR parameters:
        val shelfRsp = 240.0
        val vatPct = 20.0
        val retailerMarginPct = 25.0
        val frontDiscountPct = 5.0
        val backBonusPct = 7.0
        val logisticsPerUnit = 4.0
        val supplierCogs = 80.0

        // Execute Waterfall calculation using the exact business engine
        val result = PriceWaterfallEngine.calculate(
            shelfRspWithVat = shelfRsp,
            vatRatePct = vatPct,
            retailerMarginPct = retailerMarginPct,
            frontDiscountPct = frontDiscountPct,
            backBonusPct = backBonusPct,
            logisticsCostPerUnit = logisticsPerUnit,
            supplierCogs = supplierCogs
        )

        // Verify:
        // 1. Price without VAT = 240 / 1.20 = 200.0
        assertEquals(200.0, result.priceExVat, 0.01)

        // 2. Base Retailer Purchase Price (after 25% retail margin) = 200 * (1 - 0.25) = 150.0
        assertEquals(150.0, result.retailerPurchasePrice, 0.01)

        // 3. Invoice Price after 5% front discount = 150 * (1 - 0.05) = 142.50
        assertEquals(142.50, result.invoicePrice, 0.01)

        // 4. Back bonus 7% of invoice (or base) = 142.50 * 0.07 = 9.98
        assertTrue(result.backBonusAmount > 9.0)

        // 5. Net-Net Price = invoicePrice - backBonus - logistics
        assertTrue(result.netNetPrice > 120.0)

        // 6. Supplier Gross Profit = Net-Net - 80.00
        assertTrue(result.supplierGrossProfit > 40.0)

        // 7. Supplier Margin %
        assertTrue(result.supplierMarginPct > 30.0)
    }

    @Test
    fun `test applying extracted OCR parameters to Deal Approval and Scoring engine`() {
        // Given contract conditions extracted by OCR:
        val invoicePrice = 142.50
        val cogs = 80.0
        val volume = 10000
        val paymentDays = 45
        val annualFinancingRate = 18.0

        val scoring = DealScoringEngine.calculate(
            sellingPrice = invoicePrice,
            costPrice = cogs,
            volumeUnits = volume,
            onInvoiceDiscountPct = 0.0, // already in invoice price
            offInvoiceBonusPct = 7.0,
            paymentTermsDays = paymentDays,
            financingAnnualRatePct = annualFinancingRate,
            languageRu = true
        )

        assertNotNull(scoring)
        assertTrue(scoring.netNetPrice > 0)
        assertTrue(scoring.netNetMarginPct > 20.0)
        assertEquals(45, scoring.paymentTermsDays)
        assertEquals(10000, scoring.volumeUnits)
    }
}
