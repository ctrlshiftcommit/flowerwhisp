package com.flowerwhisp.mobile

import android.app.Application
import com.flowerwhisp.mobile.audio.AndroidAudioRecorder
import com.flowerwhisp.mobile.data.FlowerWhispDatabase
import com.flowerwhisp.mobile.data.PreferencesSettingsRepository
import com.flowerwhisp.mobile.data.PrivateDataJanitor
import com.flowerwhisp.mobile.data.RoomDictionaryRepository
import com.flowerwhisp.mobile.data.RoomHistoryRepository
import com.flowerwhisp.mobile.data.RoomSnippetRepository
import com.flowerwhisp.mobile.data.RoomTransformRepository
import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.DictionaryEntry
import com.flowerwhisp.mobile.domain.model.LanguageMode
import com.flowerwhisp.mobile.domain.model.Snippet
import com.flowerwhisp.mobile.domain.model.WritingStyle
import com.flowerwhisp.mobile.domain.ports.TextRefinementEngine
import com.flowerwhisp.mobile.domain.ports.TranscriptionEngine
import com.flowerwhisp.mobile.domain.ports.TextTransformEngine
import com.flowerwhisp.mobile.refinement.GroqTextRefinementEngine
import com.flowerwhisp.mobile.refinement.MockTextRefinementEngine
import com.flowerwhisp.mobile.service.DictationDependencies
import com.flowerwhisp.mobile.service.DictationDependencyRegistry
import com.flowerwhisp.mobile.transcription.GroqTranscriptionEngine
import com.flowerwhisp.mobile.transcription.MockTranscriptionEngine
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class FlowerWhispApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    lateinit var container: FlowerWhispContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = FlowerWhispContainer(this)
        DictationDependencyRegistry.install { container.dictationDependencies }
        applicationScope.launch {
            while (isActive) {
                runCatching {
                    val settings = container.settingsRepository.settings.first()
                    container.privateDataJanitor.run(settings)
                }
                delay(PRIVATE_DATA_MAINTENANCE_INTERVAL_MS)
            }
        }
    }

    private companion object {
        const val PRIVATE_DATA_MAINTENANCE_INTERVAL_MS = 60L * 60L * 1_000L
    }
}

class FlowerWhispContainer(application: Application) {
    val settingsRepository = PreferencesSettingsRepository(application)
    private val database = FlowerWhispDatabase.create(application)
    val historyRepository = RoomHistoryRepository(database.dictationDao(), settingsRepository)
    val dictionaryRepository = RoomDictionaryRepository(database.dictionaryEntryDao())
    val snippetRepository = RoomSnippetRepository(database.snippetDao())
    val transformRepository = RoomTransformRepository(database.transformProfileDao())
    val privateDataJanitor = PrivateDataJanitor(application, historyRepository)

    private val httpClient = OkHttpClient.Builder().build()
    private val mockTranscription = MockTranscriptionEngine()
    private val mockRefinement = MockTextRefinementEngine()
    private val groqTranscription = GroqTranscriptionEngine(settingsRepository, httpClient)
    private val groqRefinement = GroqTextRefinementEngine(settingsRepository, httpClient)

    val transcriptionEngine: TranscriptionEngine = SwitchingTranscriptionEngine(
        settings = { settingsRepository.settings.first() },
        mock = mockTranscription,
        cloud = groqTranscription,
    )
    val refinementEngine: TextRefinementEngine = SwitchingRefinementEngine(
        mock = mockRefinement,
        cloud = groqRefinement,
    )
    val transformEngine: TextTransformEngine = SwitchingTransformEngine(
        mock = mockRefinement,
        cloud = groqRefinement,
    )

    val dictationDependencies = DictationDependencies(
        audioRecorder = AndroidAudioRecorder(application),
        transcriptionEngine = transcriptionEngine,
        refinementEngine = refinementEngine,
        historyRepository = historyRepository,
        dictionaryRepository = dictionaryRepository,
        snippetRepository = snippetRepository,
        settingsRepository = settingsRepository,
    )
}

private class SwitchingTranscriptionEngine(
    private val settings: suspend () -> AppSettings,
    private val mock: TranscriptionEngine,
    private val cloud: TranscriptionEngine,
) : TranscriptionEngine {
    override suspend fun transcribe(audio: File, language: LanguageMode): String =
        if (settings().useMockEngines) mock.transcribe(audio, language) else cloud.transcribe(audio, language)
}

private class SwitchingRefinementEngine(
    private val mock: TextRefinementEngine,
    private val cloud: TextRefinementEngine,
) : TextRefinementEngine {
    override suspend fun refine(
        source: String,
        style: WritingStyle,
        settings: AppSettings,
        dictionary: List<DictionaryEntry>,
        snippets: List<Snippet>,
    ): String = if (settings.useMockEngines) {
        mock.refine(source, style, settings, dictionary, snippets)
    } else {
        cloud.refine(source, style, settings, dictionary, snippets)
    }
}

private class SwitchingTransformEngine(
    private val mock: TextTransformEngine,
    private val cloud: TextTransformEngine,
) : TextTransformEngine {
    override suspend fun transform(source: String, instructions: String, settings: AppSettings): String =
        if (settings.useMockEngines) {
            mock.transform(source, instructions, settings)
        } else {
            cloud.transform(source, instructions, settings)
        }
}
