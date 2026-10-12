package com.motocrashguardian.ui.onboarding

import com.motocrashguardian.core.model.AppSettings
import com.motocrashguardian.data.settings.SettingsRepository
import io.mockk.any
import io.mockk.coEvery
import io.mockk.firstArg
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val settingsRepository = mockk<SettingsRepository>()

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `acceptConsent persiste la fecha de consentimiento`() = runTest(testDispatcher) {
        Dispatchers.setMain(testDispatcher)
        val settingsFlow = MutableStateFlow(AppSettings())
        coEvery { settingsRepository.settings } returns settingsFlow
        coEvery { settingsRepository.updateSettings(any()) } answers {
            val transform = firstArg<(AppSettings) -> AppSettings>()
            settingsFlow.value = transform(settingsFlow.value)
        }

        val viewModel = OnboardingViewModel(settingsRepository)
        viewModel.acceptConsent()
        advanceUntilIdle()

        val consentAt: Instant? = settingsFlow.value.consentAcceptedAt
        assertNotNull(consentAt)
    }
}
