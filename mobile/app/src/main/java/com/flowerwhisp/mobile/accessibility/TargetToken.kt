package com.flowerwhisp.mobile.accessibility

/**
 * Non-sensitive identity frozen when the user explicitly starts dictation.
 *
 * Field text, selection, hints, and accessibility node instances must never be stored here.
 */
data class TargetToken(
    val packageName: String,
    val windowId: Int,
    val className: String?,
    val viewIdResourceName: String?,
    val generation: Long,
) {
    fun matches(other: TargetToken?): Boolean = other != null && this == other
}

internal data class TargetIdentity(
    val packageName: String,
    val windowId: Int,
    val className: String?,
    val viewIdResourceName: String?,
)

/** Tracks focus generations without retaining AccessibilityNodeInfo instances. */
internal class TargetGenerationTracker {
    private var identity: TargetIdentity? = null
    private var platformUniqueId: String? = null
    private var generation: Long = 0

    fun observe(
        nextIdentity: TargetIdentity?,
        nextPlatformUniqueId: String?,
        explicitFocusEvent: Boolean,
    ): TargetToken? {
        if (nextIdentity == null) {
            identity = null
            platformUniqueId = null
            generation += 1
            return null
        }

        val identityChanged = identity != nextIdentity
        val uniqueIdChanged = platformUniqueId != null &&
            nextPlatformUniqueId != null &&
            platformUniqueId != nextPlatformUniqueId
        val ambiguousExplicitRefocus = explicitFocusEvent &&
            !identityChanged &&
            platformUniqueId == null &&
            nextPlatformUniqueId == null

        if (identity == null || identityChanged || uniqueIdChanged || ambiguousExplicitRefocus) {
            generation += 1
        }
        identity = nextIdentity
        platformUniqueId = nextPlatformUniqueId
        return TargetToken(
            packageName = nextIdentity.packageName,
            windowId = nextIdentity.windowId,
            className = nextIdentity.className,
            viewIdResourceName = nextIdentity.viewIdResourceName,
            generation = generation,
        )
    }
}
