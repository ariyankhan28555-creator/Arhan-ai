package com.example

import android.content.Context
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.ai.NovaActionType
import com.example.ai.OfflineFallbackEngine
import com.example.model.AssistantLanguage
import com.example.model.DeviceSnapshot
import com.example.ui.screens.AssistantOrbStageScreen
import com.example.ui.theme.NovaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `read app name string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("NOVA AI", appName)
    }

    @Test
    fun `assistant orb stage transitions between Idle, Listening, and Thinking states`() {
        composeRule.setContent {
            NovaTheme {
                AssistantOrbStageScreen()
            }
        }

        composeRule.onNodeWithTag("orb_state_badge").assertTextEquals("IDLE")

        composeRule.onNodeWithTag("state_btn_listening").performScrollTo().performClick()
        composeRule.onNodeWithTag("orb_state_badge").assertTextEquals("LISTENING")

        composeRule.onNodeWithTag("state_btn_thinking").performScrollTo().performClick()
        composeRule.onNodeWithTag("orb_state_badge").assertTextEquals("THINKING")

        composeRule.onNodeWithTag("central_orb_indicator").performScrollTo().performClick()
        composeRule.onNodeWithTag("orb_state_badge").assertTextEquals("IDLE")
    }

    @Test
    fun `offline engine answers Hindi battery query accurately`() {
        val snapshot = DeviceSnapshot(batteryPercent = 75, isCharging = false)
        val result = OfflineFallbackEngine.evaluate(
            input = "मेरा फोन बैटरी कितना है?",
            snapshot = snapshot,
            preferredLanguage = AssistantLanguage.HINGLISH
        )
        assertEquals(NovaActionType.CHECK_BATTERY, result.action)
        assertTrue(result.spokenResponse.contains("75"))
    }

    @Test
    fun `offline engine parses Hinglish app launch command`() {
        val result = OfflineFallbackEngine.evaluate(
            input = "YouTube खोलो",
            snapshot = DeviceSnapshot(),
            preferredLanguage = AssistantLanguage.HINGLISH
        )
        assertEquals(NovaActionType.OPEN_APP, result.action)
        assertEquals("YouTube", result.param1)
    }

    @Test
    fun `offline math parser solves expressions accurately`() {
        val value = OfflineFallbackEngine.tryEvaluateMath("245 * 18 + 90")
        assertNotNull(value)
        assertEquals(4500.0, value!!, 0.0001)
    }
}
