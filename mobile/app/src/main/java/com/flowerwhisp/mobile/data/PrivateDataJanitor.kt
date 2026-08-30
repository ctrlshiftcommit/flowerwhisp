package com.flowerwhisp.mobile.data

import android.content.Context
import com.flowerwhisp.mobile.domain.model.AppSettings
import com.flowerwhisp.mobile.domain.model.RetentionMode
import java.io.File
import kotlinx.coroutines.flow.first

/** Bounds private recovery and share files without ever following a path outside app storage. */
class PrivateDataJanitor(
    context: Context,
    private val historyRepository: RoomHistoryRepository,
) {
    private val appContext = context.applicationContext
    private val recoveryDirectory = File(appContext.noBackupFilesDir, "recovery_audio")
    private val exportDirectory = File(appContext.cacheDir, "audio_exports")

    suspend fun run(settings: AppSettings, nowEpochMs: Long = System.currentTimeMillis()) {
        if (settings.retentionMode == RetentionMode.HOURS_24) {
            val cutoff = nowEpochMs - RETENTION_24_HOURS_MS
            historyRepository.pruneBefore(cutoff).forEach(::deleteOwnedRecoveryPath)
        }

        val referenced = historyRepository.observeAll().first()
            .mapNotNull { it.recoveryAudioPath }
            .mapNotNull(::canonicalOwnedRecoveryPath)
            .toSet()
        val orphanCutoff = nowEpochMs - ORPHAN_RECOVERY_MAX_AGE_MS
        recoveryDirectory.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < orphanCutoff }
            .filterNot { canonicalOwnedRecoveryPath(it.absolutePath) in referenced }
            .forEach { runCatching { it.delete() } }

        val exportCutoff = nowEpochMs - STALE_EXPORT_MAX_AGE_MS
        exportDirectory.listFiles().orEmpty()
            .filter { it.isFile && it.lastModified() < exportCutoff }
            .forEach { runCatching { it.delete() } }
    }

    private fun deleteOwnedRecoveryPath(path: String) {
        canonicalOwnedRecoveryPath(path)?.let { ownedPath ->
            runCatching { File(ownedPath).delete() }
        }
    }

    private fun canonicalOwnedRecoveryPath(path: String): String? = runCatching {
        val root = recoveryDirectory.canonicalFile
        val candidate = File(path).canonicalFile
        val rootPrefix = root.path.trimEnd(File.separatorChar) + File.separator
        candidate.path.takeIf { it.startsWith(rootPrefix) }
    }.getOrNull()

    private companion object {
        const val RETENTION_24_HOURS_MS = 24L * 60L * 60L * 1_000L
        const val ORPHAN_RECOVERY_MAX_AGE_MS = 24L * 60L * 60L * 1_000L
        const val STALE_EXPORT_MAX_AGE_MS = 60L * 60L * 1_000L
    }
}
