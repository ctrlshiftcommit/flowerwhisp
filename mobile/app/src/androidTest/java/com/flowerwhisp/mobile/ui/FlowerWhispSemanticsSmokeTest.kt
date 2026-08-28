package com.flowerwhisp.mobile.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.flowerwhisp.mobile.platform.CapabilitySnapshot
import com.flowerwhisp.mobile.ui.app.FlowerWhispActions
import com.flowerwhisp.mobile.ui.app.FlowerWhispApp
import com.flowerwhisp.mobile.ui.app.FlowerWhispDestination
import com.flowerwhisp.mobile.ui.app.FlowerWhispUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FlowerWhispSemanticsSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun readyHome_exposesReadinessBubbleAndNavigationCallback() {
        var destination: FlowerWhispDestination? = null
        composeRule.setContent {
            FlowerWhispApp(
                uiState = FlowerWhispUiState(
                    onboardingComplete = true,
                    capabilities = CapabilitySnapshot(
                        accessibilityEnabled = true,
                        overlayEnabled = true,
                        microphoneGranted = true,
                        notificationsGranted = true,
                    ),
                ),
                actions = FlowerWhispActions(onNavigate = { destination = it }),
            )
        }

        composeRule.onNodeWithText("Ready to dictate").assertIsDisplayed()
        composeRule.onNode(hasContentDescription("FlowerWhisp ready. Start dictation"), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithTag("nav-settings").assertHasClickAction().performClick()
        composeRule.runOnIdle { assertEquals(FlowerWhispDestination.SETTINGS, destination) }
    }
}
