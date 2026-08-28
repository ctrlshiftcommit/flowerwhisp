package com.flowerwhisp.mobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.BubbleOpacity
import com.flowerwhisp.mobile.domain.model.BubbleSize
import com.flowerwhisp.mobile.domain.model.DEFAULT_REFINEMENT_PROMPT
import com.flowerwhisp.mobile.domain.model.IdleBehavior
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.domain.ports.SettingsRepository
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.flowerWhispSettingsDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "flowerwhisp_settings")

private object SettingsKeys {
    val onboardingComplete = booleanPreferencesKey("onboarding_complete")
    val onboardingStep = intPreferencesKey("onboarding_step")
    val language = stringPreferencesKey("language")
    val writingStyle = stringPreferencesKey("writing_style")
    val autoPunctuation = booleanPreferencesKey("auto_punctuation")
    val removeFillers = booleanPreferencesKey("remove_fillers")
    val spokenCorrections = booleanPreferencesKey("spoken_corrections")
    val aiRefinement = booleanPreferencesKey("ai_refinement")
    val privacyMode = booleanPreferencesKey("privacy_mode")
    val bubbleSize = stringPreferencesKey("bubble_size")
    val bubbleOpacity = stringPreferencesKey("bubble_opacity")
    val idleBehavior = stringPreferencesKey("idle_behavior")
    val haptics = booleanPreferencesKey("haptics")
    val reduceMotion = booleanPreferencesKey("reduce_motion")
    val bubbleVerticalFraction = floatPreferencesKey("bubble_vertical_fraction")
    val snoozedUntilEpochMs = longPreferencesKey("snoozed_until_epoch_ms")
    val useMockEngines = booleanPreferencesKey("use_mock_engines")
    val groqTranscriptionModel = stringPreferencesKey("groq_transcription_model")
    val groqRefinementModel = stringPreferencesKey("groq_refinement_model")
    val refinementPrompt = stringPreferencesKey("refinement_prompt")
    val encryptedGroqApiKey = stringPreferencesKey("encrypted_groq_api_key")
}

internal fun Preferences.toAppSettings(): AppSettings = AppSettings(
    onboardingComplete = this[SettingsKeys.onboardingComplete] ?: false,
    onboardingStep = this[SettingsKeys.onboardingStep] ?: 0,
    language = this[SettingsKeys.language].toStoredLanguageMode(),
    writingStyle = this[SettingsKeys.writingStyle].toWritingStyle(),
    autoPunctuation = this[SettingsKeys.autoPunctuation] ?: true,
    removeFillers = this[SettingsKeys.removeFillers] ?: true,
    spokenCorrections = this[SettingsKeys.spokenCorrections] ?: true,
    aiRefinement = this[SettingsKeys.aiRefinement] ?: true,
    privacyMode = this[SettingsKeys.privacyMode] ?: false,
    bubbleSize = this[SettingsKeys.bubbleSize].toBubbleSize(),
    bubbleOpacity = this[SettingsKeys.bubbleOpacity].toBubbleOpacity(),
    idleBehavior = this[SettingsKeys.idleBehavior].toIdleBehavior(),
    haptics = this[SettingsKeys.haptics] ?: true,
    reduceMotion = this[SettingsKeys.reduceMotion] ?: false,
    bubbleVerticalFraction = this[SettingsKeys.bubbleVerticalFraction] ?: 0.68f,
    snoozedUntilEpochMs = this[SettingsKeys.snoozedUntilEpochMs] ?: 0L,
    useMockEngines = this[SettingsKeys.useMockEngines] ?: true,
    groqTranscriptionModel = this[SettingsKeys.groqTranscriptionModel]
        ?: "whisper-large-v3-turbo",
    groqRefinementModel = this[SettingsKeys.groqRefinementModel]
        ?: "llama-3.3-70b-versatile",
    refinementPrompt = this[SettingsKeys.refinementPrompt] ?: DEFAULT_REFINEMENT_PROMPT,
)

private fun MutablePreferences.applySettings(settings: AppSettings) {
    this[SettingsKeys.onboardingComplete] = settings.onboardingComplete
    this[SettingsKeys.onboardingStep] = settings.onboardingStep.coerceIn(0, 4)
    this[SettingsKeys.language] = settings.language.name
    this[SettingsKeys.writingStyle] = settings.writingStyle.name
    this[SettingsKeys.autoPunctuation] = settings.autoPunctuation
    this[SettingsKeys.removeFillers] = settings.removeFillers
    this[SettingsKeys.spokenCorrections] = settings.spokenCorrections
    this[SettingsKeys.aiRefinement] = settings.aiRefinement
    this[SettingsKeys.privacyMode] = settings.privacyMode
    this[SettingsKeys.bubbleSize] = settings.bubbleSize.name
    this[SettingsKeys.bubbleOpacity] = settings.bubbleOpacity.name
    this[SettingsKeys.idleBehavior] = settings.idleBehavior.name
    this[SettingsKeys.haptics] = settings.haptics
    this[SettingsKeys.reduceMotion] = settings.reduceMotion
    this[SettingsKeys.bubbleVerticalFraction] = settings.bubbleVerticalFraction
    this[SettingsKeys.snoozedUntilEpochMs] = settings.snoozedUntilEpochMs
    this[SettingsKeys.useMockEngines] = settings.useMockEngines
    this[SettingsKeys.groqTranscriptionModel] = settings.groqTranscriptionModel
    this[SettingsKeys.groqRefinementModel] = settings.groqRefinementModel
    this[SettingsKeys.refinementPrompt] = settings.refinementPrompt
}

private fun String?.toStoredLanguageMode(): LanguageMode =
    LanguageMode.entries.firstOrNull { it.name == this } ?: LanguageMode.AUTO

private fun String?.toWritingStyle(): WritingStyle =
    WritingStyle.entries.firstOrNull { it.name == this } ?: WritingStyle.NATURAL

private fun String?.toBubbleSize(): BubbleSize =
    BubbleSize.entries.firstOrNull { it.name == this } ?: BubbleSize.MEDIUM

private fun String?.toBubbleOpacity(): BubbleOpacity =
    BubbleOpacity.entries.firstOrNull { it.name == this } ?: BubbleOpacity.STANDARD

private fun String?.toIdleBehavior(): IdleBehavior =
    IdleBehavior.entries.firstOrNull { it.name == this } ?: IdleBehavior.SHRINK

/** Preferences-backed application settings with the Groq key kept out of the settings object. */
class PreferencesSettingsRepository(
    context: Context,
    private val apiKeyCipher: GroqApiKeyCipher = GroqApiKeyCipher(),
) : SettingsRepository {
    private val dataStore = context.applicationContext.flowerWhispSettingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .map(Preferences::toAppSettings)
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toAppSettings())
            preferences.applySettings(updated)
        }
    }

    override suspend fun setGroqApiKey(value: String) {
        if (value.isBlank()) {
            clearGroqApiKey()
            return
        }
        val encrypted = apiKeyCipher.encrypt(value)
        dataStore.edit { preferences ->
            preferences[SettingsKeys.encryptedGroqApiKey] = encrypted
        }
    }

    override suspend fun hasGroqApiKey(): Boolean = groqApiKey() != null

    override suspend fun clearGroqApiKey() {
        dataStore.edit { preferences ->
            preferences.remove(SettingsKeys.encryptedGroqApiKey)
        }
    }

    override suspend fun groqApiKey(): String? {
        val encrypted = dataStore.data.first()[SettingsKeys.encryptedGroqApiKey] ?: return null
        return try {
            apiKeyCipher.decrypt(encrypted)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

/** Direct Android Keystore AES/GCM storage for one encrypted value. */
class GroqApiKeyCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) {
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + encrypted.size)
        cipher.iv.copyInto(payload)
        encrypted.copyInto(payload, destinationOffset = cipher.iv.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > GCM_IV_SIZE_BYTES) { "Encrypted value is too short" }
        val iv = payload.copyOfRange(0, GCM_IV_SIZE_BYTES)
        val ciphertext = payload.copyOfRange(GCM_IV_SIZE_BYTES, payload.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        return cipher.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(keyAlias, null) as? SecretKey
        if (existing != null) return existing

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BITS = 128
        const val DEFAULT_KEY_ALIAS = "flowerwhisp_groq_api_key"
    }
}
