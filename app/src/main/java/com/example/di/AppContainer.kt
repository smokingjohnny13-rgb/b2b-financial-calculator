package com.example.di

import android.content.Context
import com.example.data.ai.AiDealRiskAuditor
import com.example.data.ai.CommercialOcrScanner
import com.example.data.repository.AuthRepository
import com.example.data.repository.ClientNegotiationRepository
import com.example.data.repository.DealRepository
import com.example.ui.viewmodel.BigDealViewModel

/**
 * Lightweight manual Dependency Injection container (no Hilt/KSP overhead).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val authRepository: AuthRepository by lazy { AuthRepository(appContext) }
    val dealRepository: DealRepository by lazy { DealRepository(appContext) }
    val clientRepository: ClientNegotiationRepository by lazy { ClientNegotiationRepository(appContext) }
    val aiRiskAuditor: AiDealRiskAuditor by lazy { AiDealRiskAuditor() }
    val commercialOcrScanner: CommercialOcrScanner by lazy { CommercialOcrScanner() }

    fun createBigDealViewModel(): BigDealViewModel = BigDealViewModel(
        dealRepository = dealRepository,
        authRepository = authRepository,
        clientRepository = clientRepository,
        aiRiskAuditor = aiRiskAuditor,
        commercialOcrScanner = commercialOcrScanner
    )
}
