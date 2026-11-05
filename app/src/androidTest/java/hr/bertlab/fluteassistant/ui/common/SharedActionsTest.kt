// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.ActionListen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/** The pieces the flute's "Your turn" shares with the tuner: the microphone card's way back and the stacked big button. */
@RunWith(AndroidJUnit4::class)
class SharedActionsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun the_microphone_card_offers_a_way_back_only_when_asked() {
        var dismissals = 0
        var requests = 0
        val dismissible = mutableStateOf(false)
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                MicrophonePermissionCard(
                    onRequestPermission = { requests++ },
                    onOpenSettings = {},
                    onDismiss = if (dismissible.value) ({ dismissals++ }) else null,
                )
            }
        }
        compose.onNodeWithText("Can I listen?").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertDoesNotExist()
        dismissible.value = true
        compose.waitForIdle()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, dismissals)
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, requests)
    }

    @Test
    fun the_microphone_card_speaks_croatian() {
        compose.setContent {
            LocaleProvider(Locale("hr")) {
                MicrophonePermissionCard(onRequestPermission = {}, onOpenSettings = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("Smijem li slušati?").assertIsDisplayed()
        compose.onNodeWithText("Da").assertIsDisplayed()
        compose.onNodeWithText("Postavke").assertIsDisplayed()
        compose.onNodeWithText("Ne sad").assertIsDisplayed()
    }

    @Test
    fun the_cards_two_pills_fit_a_narrow_phone_at_large_text() {
        // "Not now" is the only way back to the tune: it must stay whole, on one line, inside the card, beside Settings.
        compose.setContent {
            LocaleProvider(Locale("hr")) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                    Box(Modifier.width(360.dp)) {
                        MicrophonePermissionCard(onRequestPermission = {}, onOpenSettings = {}, onDismiss = {})
                    }
                }
            }
        }
        compose.onNodeWithText("Smijem li slušati?").assertIsDisplayed()
        for (label in listOf("Postavke", "Ne sad")) {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val layout = layouts.single()
            assertEquals("$label should stay on one line", 1, layout.lineCount)
            assertFalse("$label should shrink, not overflow", layout.hasVisualOverflow || layout.isLineEllipsized(0))
        }
        val settings = compose.onNodeWithText("Postavke").getBoundsInRoot()
        val notNow = compose.onNodeWithText("Ne sad").getBoundsInRoot()
        // Beside Settings when they fit, under it when they do not; never over it and never past the card's edge.
        val apart = settings.right <= notNow.left || settings.bottom <= notNow.top
        assertTrue("the pills $settings and $notNow should not overlap", apart)
        assertTrue("the pills should stay inside 360 dp: $settings $notNow", settings.left >= 0.dp && settings.right <= 360.dp && notNow.left >= 0.dp && notNow.right <= 360.dp)
    }

    @Test
    fun a_stacked_big_button_centres_its_word_under_the_icon() {
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                Column {
                    BigActionButton(icon = AppIcons.Play, text = "Beside", color = ActionGo, onClick = {}, modifier = Modifier.width(220.dp))
                    BigActionButton(
                        icon = AppIcons.Microphone,
                        text = "Stacked",
                        color = ActionListen,
                        onClick = {},
                        modifier = Modifier.width(220.dp),
                        stacked = true,
                    )
                }
            }
        }
        val besideButton = compose.onNodeWithText("Beside").getBoundsInRoot()
        val besideWord = compose.onNodeWithText("Beside", useUnmergedTree = true).getBoundsInRoot()
        val stackedButton = compose.onNodeWithText("Stacked").getBoundsInRoot()
        val stackedWord = compose.onNodeWithText("Stacked", useUnmergedTree = true).getBoundsInRoot()
        val besideCentre = (besideWord.left + besideWord.right) / 2
        val stackedCentre = (stackedWord.left + stackedWord.right) / 2
        assertTrue("beside its icon the word sits right of the middle: $besideWord in $besideButton", besideCentre > (besideButton.left + besideButton.right) / 2 + 8.dp)
        assertTrue("under its icon the word is centred: $stackedWord in $stackedButton", kotlin.math.abs((stackedCentre - (stackedButton.left + stackedButton.right) / 2).value) <= 1.5f)
        assertEquals("both buttons keep the big-button height", besideButton.height, stackedButton.height)
        assertTrue("the word stands below the top half, under the icon", stackedWord.top > stackedButton.top + stackedButton.height / 2)
    }
}
