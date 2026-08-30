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

internal data class TargetBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Tracks focus generations without retaining AccessibilityNodeInfo instances. */
internal class TargetGenerationTracker {
    private var identity: TargetIdentity? = null
    private var platformUniqueId: String? = null
    private var bounds: TargetBounds? = null
    private var generation: Long = 0

    fun observe(
        nextIdentity: TargetIdentity?,
        nextPlatformUniqueId: String?,
        explicitFocusEvent: Boolean,
        nextBounds: TargetBounds? = null,
    ): TargetToken? {
        if (nextIdentity == null) {
            identity = null
            platformUniqueId = null
            bounds = null
            generation += 1
            return null
        }

        val identityChanged = identity != nextIdentity
        val uniqueIdChanged = platformUniqueId != null &&
            nextPlatformUniqueId != null &&
            platformUniqueId != nextPlatformUniqueId
        val anonymousFieldChanged = explicitFocusEvent &&
            !identityChanged &&
            platformUniqueId == null &&
            nextPlatformUniqueId == null &&
            bounds != null &&
            nextBounds != null &&
            bounds != nextBounds

        // Duplicate TYPE_VIEW_FOCUSED events are common when an overlay is tapped.
        // Bounds are consulted only for an explicit focus change, so a field
        // resizing after insertion does not invalidate its frozen generation.
        if (identity == null || identityChanged || uniqueIdChanged || anonymousFieldChanged) {
            generation += 1
        }
        identity = nextIdentity
        platformUniqueId = nextPlatformUniqueId
        bounds = nextBounds
        return TargetToken(
            packageName = nextIdentity.packageName,
            windowId = nextIdentity.windowId,
            className = nextIdentity.className,
            viewIdResourceName = nextIdentity.viewIdResourceName,
            generation = generation,
        )
    }
}
