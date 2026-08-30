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
import com.flowerwhisp.mobile.domain.model.AppearanceMode
import com.flowerwhisp.mobile.domain.model.BubbleOpacity
import com.flowerwhisp.mobile.domain.model.BubbleSize
import com.flowerwhisp.mobile.domain.model.CleanupLevel
import com.flowerwhisp.mobile.domain.model.DEFAULT_CLEANUP_PROMPT_LIGHT
import com.flowerwhisp.mobile.domain.model.DEFAULT_CLEANUP_PROMPT_MEDIUM
import com.flowerwhisp.mobile.domain.model.DEFAULT_CLEANUP_PROMPT_NONE
import com.flowerwhisp.mobile.domain.model.IdleBehavior
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.RetentionMode
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
    val personalWritingStyle = stringPreferencesKey("personal_writing_style")
    val workWritingStyle = stringPreferencesKey("work_writing_style")
    val emailWritingStyle = stringPreferencesKey("email_writing_style")
    val otherWritingStyle = stringPreferencesKey("other_writing_style")
    val personalStyleInstructions = stringPreferencesKey("personal_style_instructions")
    val workStyleInstructions = stringPreferencesKey("work_style_instructions")
    val emailStyleInstructions = stringPreferencesKey("email_style_instructions")
    val otherStyleInstructions = stringPreferencesKey("other_style_instructions")
    val autoPunctuation = booleanPreferencesKey("auto_punctuation")
    val removeFillers = booleanPreferencesKey("remove_fillers")
    val spokenCorrections = booleanPreferencesKey("spoken_corrections")
    val aiRefinement = booleanPreferencesKey("ai_refinement")
    val privacyMode = booleanPreferencesKey("privacy_mode")
    val cleanupLevel = stringPreferencesKey("cleanup_level")
    val cleanupPromptNone = stringPreferencesKey("cleanup_prompt_none")
    val cleanupPromptLight = stringPreferencesKey("cleanup_prompt_light")
    val cleanupPromptMedium = stringPreferencesKey("cleanup_prompt_medium")
    val retentionMode = stringPreferencesKey("retention_mode")
    val appearanceMode = stringPreferencesKey("appearance_mode")
    val scratchpad = stringPreferencesKey("scratchpad")
    val bubbleSize = stringPreferencesKey("bubble_size")
    val bubbleOpacity = stringPreferencesKey("bubble_opacity")
    val idleBehavior = stringPreferencesKey("idle_behavior")
    val haptics = booleanPreferencesKey("haptics")
    val playSounds = booleanPreferencesKey("play_sounds")
    val muteMusicWhileDictating = booleanPreferencesKey("mute_music_while_dictating")
    val reduceMotion = booleanPreferencesKey("reduce_motion")
    val bubbleVerticalFraction = floatPreferencesKey("bubble_vertical_fraction")
    val snoozedUntilEpochMs = longPreferencesKey("snoozed_until_epoch_ms")
    val useMockEngines = booleanPreferencesKey("use_mock_engines")
    val groqTranscriptionModel = stringPreferencesKey("groq_transcription_model")
    val groqRefinementModel = stringPreferencesKey("groq_refinement_model")
    val refinementPrompt = stringPreferencesKey("refinement_prompt")
    val encryptedGroqApiKey = stringPreferencesKey("encrypted_groq_api_key")
}

internal fun Preferences.toAppSettings(): AppSettings {
    val cleanupLevel = this[SettingsKeys.cleanupLevel].toCleanupLevel(
        legacyEnabled = this[SettingsKeys.aiRefinement] ?: true,
    )
    val retentionMode = this[SettingsKeys.retentionMode].toRetentionMode(
        legacyPrivate = this[SettingsKeys.privacyMode] ?: false,
    )
    val lightPrompt = this[SettingsKeys.cleanupPromptLight]
        ?: this[SettingsKeys.refinementPrompt]
        ?: DEFAULT_CLEANUP_PROMPT_LIGHT
    return AppSettings(
        onboardingComplete = this[SettingsKeys.onboardingComplete] ?: false,
        onboardingStep = this[SettingsKeys.onboardingStep] ?: 0,
        language = this[SettingsKeys.language].toStoredLanguageMode(),
        writingStyle = this[SettingsKeys.writingStyle].toWritingStyle(),
        personalWritingStyle = this[SettingsKeys.personalWritingStyle].toWritingStyle(WritingStyle.CASUAL),
        workWritingStyle = this[SettingsKeys.workWritingStyle].toWritingStyle(WritingStyle.PROFESSIONAL),
        emailWritingStyle = this[SettingsKeys.emailWritingStyle].toWritingStyle(WritingStyle.PROFESSIONAL),
        otherWritingStyle = this[SettingsKeys.otherWritingStyle].toWritingStyle(WritingStyle.NATURAL),
        personalStyleInstructions = this[SettingsKeys.personalStyleInstructions].orEmpty(),
        workStyleInstructions = this[SettingsKeys.workStyleInstructions].orEmpty(),
        emailStyleInstructions = this[SettingsKeys.emailStyleInstructions].orEmpty(),
        otherStyleInstructions = this[SettingsKeys.otherStyleInstructions].orEmpty(),
        autoPunctuation = this[SettingsKeys.autoPunctuation] ?: true,
        removeFillers = this[SettingsKeys.removeFillers] ?: true,
        spokenCorrections = this[SettingsKeys.spokenCorrections] ?: true,
        aiRefinement = cleanupLevel != CleanupLevel.NONE,
        privacyMode = retentionMode == RetentionMode.NEVER,
        cleanupLevel = cleanupLevel,
        cleanupPromptNone = this[SettingsKeys.cleanupPromptNone] ?: DEFAULT_CLEANUP_PROMPT_NONE,
        cleanupPromptLight = lightPrompt,
        cleanupPromptMedium = this[SettingsKeys.cleanupPromptMedium] ?: DEFAULT_CLEANUP_PROMPT_MEDIUM,
        retentionMode = retentionMode,
        appearanceMode = this[SettingsKeys.appearanceMode].toAppearanceMode(),
        scratchpad = this[SettingsKeys.scratchpad].orEmpty(),
        bubbleSize = this[SettingsKeys.bubbleSize].toBubbleSize(),
        bubbleOpacity = this[SettingsKeys.bubbleOpacity].toBubbleOpacity(),
        idleBehavior = this[SettingsKeys.idleBehavior].toIdleBehavior(),
        haptics = this[SettingsKeys.haptics] ?: true,
        playSounds = this[SettingsKeys.playSounds] ?: false,
        muteMusicWhileDictating = this[SettingsKeys.muteMusicWhileDictating] ?: false,
        reduceMotion = this[SettingsKeys.reduceMotion] ?: false,
        bubbleVerticalFraction = this[SettingsKeys.bubbleVerticalFraction] ?: 0.68f,
        snoozedUntilEpochMs = this[SettingsKeys.snoozedUntilEpochMs] ?: 0L,
        // Earlier development builds persisted mock mode as the default. Never carry that
        // fake provider into a normal upgraded install.
        useMockEngines = false,
        groqTranscriptionModel = this[SettingsKeys.groqTranscriptionModel]
            ?: "whisper-large-v3",
        groqRefinementModel = when (val stored = this[SettingsKeys.groqRefinementModel]) {
            null -> "openai/gpt-oss-20b"
            "llama-3.3-70b-versatile" -> "openai/gpt-oss-120b"
            else -> stored
        },
        refinementPrompt = lightPrompt,
    )
}

private fun MutablePreferences.applySettings(settings: AppSettings) {
    this[SettingsKeys.onboardingComplete] = settings.onboardingComplete
    this[SettingsKeys.onboardingStep] = settings.onboardingStep.coerceIn(0, 5)
    this[SettingsKeys.language] = settings.language.name
    this[SettingsKeys.writingStyle] = settings.writingStyle.name
    this[SettingsKeys.personalWritingStyle] = settings.personalWritingStyle.name
    this[SettingsKeys.workWritingStyle] = settings.workWritingStyle.name
    this[SettingsKeys.emailWritingStyle] = settings.emailWritingStyle.name
    this[SettingsKeys.otherWritingStyle] = settings.otherWritingStyle.name
    this[SettingsKeys.personalStyleInstructions] = settings.personalStyleInstructions
    this[SettingsKeys.workStyleInstructions] = settings.workStyleInstructions
    this[SettingsKeys.emailStyleInstructions] = settings.emailStyleInstructions
    this[SettingsKeys.otherStyleInstructions] = settings.otherStyleInstructions
    this[SettingsKeys.autoPunctuation] = settings.autoPunctuation
    this[SettingsKeys.removeFillers] = settings.removeFillers
    this[SettingsKeys.spokenCorrections] = settings.spokenCorrections
    this[SettingsKeys.aiRefinement] = settings.cleanupLevel != CleanupLevel.NONE
    this[SettingsKeys.privacyMode] = settings.retentionMode == RetentionMode.NEVER
    this[SettingsKeys.cleanupLevel] = settings.cleanupLevel.name
    this[SettingsKeys.cleanupPromptNone] = settings.cleanupPromptNone
    this[SettingsKeys.cleanupPromptLight] = settings.cleanupPromptLight
    this[SettingsKeys.cleanupPromptMedium] = settings.cleanupPromptMedium
    this[SettingsKeys.retentionMode] = settings.retentionMode.name
    this[SettingsKeys.appearanceMode] = settings.appearanceMode.name
    this[SettingsKeys.scratchpad] = settings.scratchpad
    this[SettingsKeys.bubbleSize] = settings.bubbleSize.name
    this[SettingsKeys.bubbleOpacity] = settings.bubbleOpacity.name
    this[SettingsKeys.idleBehavior] = settings.idleBehavior.name
    this[SettingsKeys.haptics] = settings.haptics
    this[SettingsKeys.playSounds] = settings.playSounds
    this[SettingsKeys.muteMusicWhileDictating] = settings.muteMusicWhileDictating
    this[SettingsKeys.reduceMotion] = settings.reduceMotion
    this[SettingsKeys.bubbleVerticalFraction] = settings.bubbleVerticalFraction
    this[SettingsKeys.snoozedUntilEpochMs] = settings.snoozedUntilEpochMs
    this[SettingsKeys.useMockEngines] = settings.useMockEngines
    this[SettingsKeys.groqTranscriptionModel] = settings.groqTranscriptionModel
    this[SettingsKeys.groqRefinementModel] = settings.groqRefinementModel
    this[SettingsKeys.refinementPrompt] = settings.cleanupPromptLight
}

private fun String?.toStoredLanguageMode(): LanguageMode =
    LanguageMode.entries.firstOrNull { it.name == this } ?: LanguageMode.AUTO

private fun String?.toWritingStyle(fallback: WritingStyle = WritingStyle.NATURAL): WritingStyle =
    WritingStyle.entries.firstOrNull { it.name == this } ?: fallback

private fun String?.toCleanupLevel(legacyEnabled: Boolean): CleanupLevel =
    CleanupLevel.entries.firstOrNull { it.name == this }
        ?: if (legacyEnabled) CleanupLevel.LIGHT else CleanupLevel.NONE

private fun String?.toRetentionMode(legacyPrivate: Boolean): RetentionMode =
    RetentionMode.entries.firstOrNull { it.name == this }
        ?: if (legacyPrivate) RetentionMode.NEVER else RetentionMode.FOREVER

private fun String?.toAppearanceMode(): AppearanceMode =
    AppearanceMode.entries.firstOrNull { it.name == this } ?: AppearanceMode.DARK

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
