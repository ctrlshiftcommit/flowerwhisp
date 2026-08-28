package com.flowerwhisp.mobile.service

import android.content.Context
import com.flowerwhisp.mobile.domain.ports.AudioRecorder
import com.flowerwhisp.mobile.domain.ports.DictionaryRepository
import com.flowerwhisp.mobile.domain.ports.HistoryRepository
import com.flowerwhisp.mobile.domain.ports.SettingsRepository
import com.flowerwhisp.mobile.domain.ports.SnippetRepository
import com.flowerwhisp.mobile.domain.ports.TextRefinementEngine
import com.flowerwhisp.mobile.domain.ports.TranscriptionEngine
import java.io.File
import java.util.UUID

data class DictationDependencies(
    val audioRecorder: AudioRecorder,
    val transcriptionEngine: TranscriptionEngine,
    val refinementEngine: TextRefinementEngine,
    val historyRepository: HistoryRepository,
    val dictionaryRepository: DictionaryRepository,
    val snippetRepository: SnippetRepository,
    val settingsRepository: SettingsRepository,
    val recordingFileFactory: RecordingFileFactory = AppPrivateRecordingFileFactory,
)

fun interface RecordingFileFactory {
    fun create(context: Context): File
}

object AppPrivateRecordingFileFactory : RecordingFileFactory {
    override fun create(context: Context): File {
        val directory = File(context.noBackupFilesDir, "recovery_audio")
        check(directory.exists() || directory.mkdirs()) { "Unable to create the private recovery directory" }
        return File(directory, "dictation-${UUID.randomUUID()}.m4a")
    }
}

/**
 * Application wiring installs one provider during process startup. Keeping this registry narrow lets the
 * service compile before the concurrent FlowerWhispApplication/AppContainer implementation lands.
 */
object DictationDependencyRegistry {
    @Volatile
    private var provider: (() -> DictationDependencies)? = null

    fun install(provider: () -> DictationDependencies) {
        this.provider = provider
    }

    fun clear() {
        provider = null
    }

    fun peek(): DictationDependencies? = provider?.invoke()
}
