package com.example

import com.example.data.model.CargoCategory
import com.example.domain.calculator.LandedCostCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LandedCostCalculatorTest {

    @Test
    fun testDirectCalculation() {
        // FOB = $10,000, Coefficient = 1.48
        // Landed Cost = $14,800
        val result = LandedCostCalculator.calculateDirect(
            fobPrice = 10000.0,
            coefficient = 1.48
        )
        assertEquals(10000.0, result.fobPrice, 0.01)
        assertEquals(14800.0, result.landedCost, 0.01)
        assertEquals(4800.0, result.totalOverheadAmount, 0.01)
        assertEquals(48.0, result.totalOverheadPct, 0.01)
    }

    @Test
    fun testReverseCalculation() {
        // Target Landed Cost = $14,800, Coefficient = 1.48
        // Max allowable FOB = $14,800 / 1.48 = $10,000
        val result = LandedCostCalculator.calculateReverse(
            targetLandedCost = 14800.0,
            coefficient = 1.48
        )
        assertEquals(10000.0, result.fobPrice, 0.01)
        assertEquals(14800.0, result.landedCost, 0.01)
        assertTrue(result.isReverseCalculation)
    }

    @Test
    fun testCoefficientCalibration() {
        // Known FOB = $10,000, Known Landed Cost = $13,000 (e.g. electronics)
        val coeff = LandedCostCalculator.calibrateCoefficient(
            fobPrice = 10000.0,
            landedCost = 13000.0
        )
        assertEquals(1.30, coeff, 0.001)
    }

    @Test
    fun testBuildCoefficientFromComponents() {
        // Logistics = 15%, Duty = 7.5%, VAT = 20%, Broker = 2.5%
        // (1 + 0.15) * (1 + 0.075) * (1 + 0.20) + 0.025
        // 1.15 * 1.075 * 1.20 + 0.025 = 1.4835 + 0.025 = 1.5085
        val coeff = LandedCostCalculator.buildCoefficientFromComponents(
            logisticsPct = 15.0,
            dutyPct = 7.5,
            vatPct = 20.0,
            brokerFeesPct = 2.5
        )
        assertEquals(1.5085, coeff, 0.001)
    }

    @Test
    fun testCargoCategoriesPresetRanges() {
        // Electronics
        assertEquals(1.28, CargoCategory.ELECTRONICS.minCoeff, 0.001)
        assertEquals(1.32, CargoCategory.ELECTRONICS.maxCoeff, 0.001)

        // Apparel
        assertEquals(1.43, CargoCategory.APPAREL.minCoeff, 0.001)
        assertEquals(1.48, CargoCategory.APPAREL.maxCoeff, 0.001)

        // Industrial
        assertEquals(1.48, CargoCategory.INDUSTRIAL.defaultCoeff, 0.001)

        // Bulky
        assertEquals(1.65, CargoCategory.BULKY.minCoeff, 0.001)
        assertEquals(1.75, CargoCategory.BULKY.maxCoeff, 0.001)
    }

    @Test
    fun testLandedCostIntegrationWithDealScoringEngine() {
        // Direct calculation: FOB $50.0 with 1.48 coeff = Landed Cost $74.0
        val landedResult = LandedCostCalculator.calculateDirect(
            fobPrice = 50.0,
            coefficient = 1.48
        )
        assertEquals(74.0, landedResult.landedCost, 0.01)

        // Transfer landed cost into Deal Scoring as cost price (COGS)
        val sellingPrice = 114.0
        val dealScoring = com.example.domain.calculator.DealScoringEngine.calculate(
            sellingPrice = sellingPrice,
            costPrice = landedResult.landedCost,
            volumeUnits = 1000,
            onInvoiceDiscountPct = 5.0,
            offInvoiceBonusPct = 5.0,
            paymentTermsDays = 30,
            financingAnnualRatePct = 18.0
        )

        // Gross margin: (114 - 74) / 114 = 35.09%
        assertEquals(35.09, dealScoring.grossMarginPct, 0.05)
        // Total cost of goods sold = 74.0 * 1000 = 74,000
        assertEquals(74000.0, dealScoring.costPrice * dealScoring.volumeUnits, 0.01)
        // Deal must be profitable after accounting for landed cost
        assertTrue(dealScoring.totalNetNetProfit > 0.0)
        assertTrue(dealScoring.profitAfterCostOfCapital > 0.0)
    }
}
