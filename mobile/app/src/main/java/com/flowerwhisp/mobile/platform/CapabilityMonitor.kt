package com.flowerwhisp.mobile.platform

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class CapabilitySnapshot(
    val accessibilityEnabled: Boolean,
    val accessibilityConnected: Boolean = accessibilityEnabled,
    val overlayEnabled: Boolean,
    val microphoneGranted: Boolean,
    val notificationsGranted: Boolean,
) {
    val textInsertionReady: Boolean get() = accessibilityEnabled && accessibilityConnected
    val canShowBubble: Boolean get() = textInsertionReady && overlayEnabled
    val canRecord: Boolean get() = canShowBubble && microphoneGranted
}

class CapabilityMonitor(private val context: Context) {
    fun snapshot(accessibilityConnected: Boolean = false): CapabilitySnapshot = CapabilitySnapshot(
        accessibilityEnabled = accessibilityConnected || isAccessibilityEnabled(),
        accessibilityConnected = accessibilityConnected,
        overlayEnabled = Settings.canDrawOverlays(context),
        microphoneGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
        notificationsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
    )

    private fun isAccessibilityEnabled(): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java)
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == context.packageName }
    }
}
