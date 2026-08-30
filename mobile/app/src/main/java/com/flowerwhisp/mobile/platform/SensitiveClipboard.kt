package com.flowerwhisp.mobile.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import java.util.UUID

/**
 * Copies a transcript as sensitive, temporary clipboard data.
 *
 * The unique label lets an old clear task prove it still owns the clipboard,
 * so a later user copy is never erased. The delayed task retains no transcript.
 */
object SensitiveClipboard {
    private const val AUTO_CLEAR_AFTER_MS = 2 * 60 * 1_000L

    fun copy(context: Context, text: String): Boolean {
        if (text.isBlank()) return false
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val ownershipLabel = "FlowerWhisp-${UUID.randomUUID()}"
        val clip = ClipData.newPlainText(ownershipLabel, text).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        return runCatching {
            clipboard.setPrimaryClip(clip)
            Handler(Looper.getMainLooper()).postDelayed(
                {
                    val currentLabel = clipboard.primaryClipDescription?.label?.toString()
                    if (currentLabel == ownershipLabel) clipboard.clearPrimaryClip()
                },
                AUTO_CLEAR_AFTER_MS,
            )
        }.isSuccess
    }
}
