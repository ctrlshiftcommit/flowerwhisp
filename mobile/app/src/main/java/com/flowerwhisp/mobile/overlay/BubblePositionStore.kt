package com.flowerwhisp.mobile.overlay

import com.flowerwhisp.mobile.domain.ports.SettingsRepository
import kotlinx.coroutines.flow.first

interface BubblePositionStore {
    suspend fun loadVerticalFraction(): Float
    suspend fun saveVerticalFraction(value: Float)
}

class SettingsBubblePositionStore(
    private val settingsRepository: SettingsRepository,
) : BubblePositionStore {
    override suspend fun loadVerticalFraction(): Float =
        settingsRepository.settings.first().bubbleVerticalFraction.coerceIn(0f, 1f)

    override suspend fun saveVerticalFraction(value: Float) {
        settingsRepository.update { it.copy(bubbleVerticalFraction = value.coerceIn(0f, 1f)) }
    }
}
