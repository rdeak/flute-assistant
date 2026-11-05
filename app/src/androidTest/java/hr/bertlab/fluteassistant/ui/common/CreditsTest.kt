// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CreditsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(locale: Locale = Locale.ENGLISH) {
        compose.setContent {
            LocaleProvider(locale) {
                CreditsLogo()
            }
        }
    }

    private fun logo() = compose.onNodeWithContentDescription("Flute Assistant")

    private fun holder() = compose.onNodeWithText("Bertlab d.o.o.", substring = true)

    private fun privacyLink() = compose.onNodeWithText("/release/privacy-policy", substring = true)

    private fun licenceLink() = compose.onNodeWithText("gnu.org/licenses/gpl-3.0", substring = true)

    @Test
    fun a_tap_on_the_logo_opens_nothing() {
        show()
        logo().performTouchInput { click() }
        holder().assertDoesNotExist()
    }

    @Test
    fun a_long_press_shows_who_holds_the_copyright() {
        show()
        logo().performTouchInput { longClick() }
        holder().assertIsDisplayed()
    }

    @Test
    fun the_credits_leave_out_the_repository_address() {
        show()
        logo().performTouchInput { longClick() }
        compose.onNodeWithText("github.com/rdeak/", substring = true).assertDoesNotExist()
    }

    @Test
    fun a_long_press_shows_the_installed_version() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val version = app.packageManager.getPackageInfo(app.packageName, 0).versionName
        show()
        logo().performTouchInput { longClick() }
        compose.onNodeWithText("Version $version").assertIsDisplayed()
    }

    @Test
    fun a_long_press_shows_where_the_privacy_policy_is() {
        show()
        logo().performTouchInput { longClick() }
        privacyLink().assertIsDisplayed()
    }

    @Test
    fun the_privacy_address_is_plain_text_so_a_child_cannot_leave_the_app() {
        show()
        logo().performTouchInput { longClick() }
        privacyLink().assertHasNoClickAction()
    }

    @Test
    fun the_version_and_privacy_lines_are_in_croatian_on_a_croatian_phone() {
        show(Locale("hr"))
        logo().performTouchInput { longClick() }
        compose.onNodeWithText("Verzija", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Pravila privatnosti", substring = true).assertIsDisplayed()
    }

    @Test
    fun the_privacy_policy_comes_above_the_copyright() {
        show()
        logo().performTouchInput { longClick() }
        val privacy = privacyLink().fetchSemanticsNode().boundsInRoot.top
        val copyright = holder().fetchSemanticsNode().boundsInRoot.top
        assertTrue(privacy < copyright)
    }

    @Test
    fun the_licence_says_the_app_may_be_shared_and_changed() {
        show()
        logo().performTouchInput { longClick() }
        compose.onNodeWithText("share and change", substring = true).assertIsDisplayed()
    }

    @Test
    fun a_long_press_shows_where_to_read_the_licence() {
        show()
        logo().performTouchInput { longClick() }
        licenceLink().assertIsDisplayed()
    }

    @Test
    fun the_licence_address_is_plain_text_so_a_child_cannot_leave_the_app() {
        show()
        logo().performTouchInput { longClick() }
        licenceLink().assertHasNoClickAction()
    }

    @Test
    fun the_licence_lines_are_in_croatian_on_a_croatian_phone() {
        show(Locale("hr"))
        logo().performTouchInput { longClick() }
        compose.onNodeWithText("dijeliti i mijenjati", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Tekst licencije", substring = true).assertIsDisplayed()
    }

    @Test
    fun ok_closes_the_credits() {
        show()
        logo().performTouchInput { longClick() }
        compose.onNodeWithText("OK").performClick()
        holder().assertDoesNotExist()
    }

    @Test
    fun a_screen_reader_can_open_the_credits() {
        show()
        logo().performSemanticsAction(SemanticsActions.OnLongClick)
        holder().assertIsDisplayed()
    }

    @Test
    fun the_credits_are_in_croatian_on_a_croatian_phone() {
        show(Locale("hr"))
        logo().performTouchInput { longClick() }
        compose.onNodeWithText("Autorska prava", substring = true).assertIsDisplayed()
        compose.onNodeWithText("U redu").assertIsDisplayed()
    }
}
