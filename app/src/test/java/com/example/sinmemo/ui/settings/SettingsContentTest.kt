package com.example.sinmemo.ui.settings

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.R
import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.data.settings.ThemeMode
import com.example.sinmemo.data.settings.UserSettings
import com.example.sinmemo.ui.theme.SinMemoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S-03・D-03・D-04（SettingsContent）の UI テスト。Robolectric で PC 上で動かす（設定 テスト設計 1 章）。 */
@RunWith(RobolectricTestRunner::class)
class SettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun str(id: Int) = context.getString(id)

    private val events = mutableListOf<String>()

    private fun show(state: SettingsUiState, versionName: String = "1.0.0") {
        composeRule.setContent {
            SinMemoTheme {
                SettingsContent(
                    uiState = state,
                    versionName = versionName,
                    onBack = { events += "back" },
                    onThemeClick = { events += "theme" },
                    onFontSizeClick = { events += "fontSize" },
                    onThemeSelect = { events += "themeSelect:$it" },
                    onFontSizeSelect = { events += "fontSizeSelect:$it" },
                    onDialogConfirm = { events += "ok" },
                    onDialogDismiss = { events += "dismiss" },
                )
            }
        }
    }

    @Test
    fun showsItems_withCurrentValues_andVersion() {
        show(SettingsUiState(UserSettings(ThemeMode.LIGHT, FontSizeSetting.MEDIUM)))

        composeRule.onNodeWithText(str(R.string.settings_title)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.theme)).assertIsDisplayed()
        composeRule.onNodeWithText("ライト").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.font_size)).assertIsDisplayed()
        composeRule.onNodeWithText("中").assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.version)).assertIsDisplayed()
        composeRule.onNodeWithText("1.0.0").assertIsDisplayed()
    }

    @Test
    fun showsOtherValues() {
        show(SettingsUiState(UserSettings(ThemeMode.SYSTEM, FontSizeSetting.LARGE)), versionName = "9.8.7")

        composeRule.onNodeWithText("端末の設定に従う").assertIsDisplayed()
        composeRule.onNodeWithText("大").assertIsDisplayed()
        composeRule.onNodeWithText("9.8.7").assertIsDisplayed()
    }

    @Test
    fun clickItems_callCallbacks_versionIsNotClickable() {
        show(SettingsUiState())

        composeRule.onNodeWithText(str(R.string.theme)).performClick()
        composeRule.onNodeWithText(str(R.string.font_size)).performClick()
        composeRule.onNodeWithContentDescription(str(R.string.back)).performClick()

        assertEquals(listOf("theme", "fontSize", "back"), events)
        // 押せるのは ←・テーマ・文字サイズの 3 つだけ（バージョンは押せない）
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(3)
    }

    @Test
    fun themeDialog_showsOptions_withMark() {
        show(SettingsUiState(dialog = SettingsDialog.Theme(selected = ThemeMode.DARK)))

        composeRule.onAllNodes(isSelectable()).assertCountEquals(3)
        composeRule.onNode(isSelectable() and hasText("端末の設定に従う")).assertIsNotSelected()
        composeRule.onNode(isSelectable() and hasText("ライト")).assertIsNotSelected()
        composeRule.onNode(isSelectable() and hasText("ダーク")).assertIsSelected()
        composeRule.onNodeWithText(str(R.string.ok)).assertIsDisplayed()
        composeRule.onNodeWithText(str(R.string.cancel)).assertIsDisplayed()
    }

    @Test
    fun themeDialog_select_ok_cancel() {
        show(SettingsUiState(dialog = SettingsDialog.Theme(selected = ThemeMode.LIGHT)))

        composeRule.onNode(isSelectable() and hasText("端末の設定に従う")).performClick()
        composeRule.onNodeWithText(str(R.string.ok)).performClick()
        composeRule.onNodeWithText(str(R.string.cancel)).performClick()

        assertEquals(listOf("themeSelect:SYSTEM", "ok", "dismiss"), events)
    }

    @Test
    fun fontSizeDialog_showsOptions_withMark_andSelect() {
        show(SettingsUiState(dialog = SettingsDialog.FontSize(selected = FontSizeSetting.MEDIUM)))

        composeRule.onAllNodes(isSelectable()).assertCountEquals(3)
        composeRule.onNode(isSelectable() and hasText("小")).assertIsNotSelected()
        composeRule.onNode(isSelectable() and hasText("中")).assertIsSelected()
        composeRule.onNode(isSelectable() and hasText("大")).assertIsNotSelected()

        composeRule.onNode(isSelectable() and hasText("大")).performClick()

        assertEquals(listOf("fontSizeSelect:LARGE"), events)
    }

    @Test
    fun noDialog_whenNull() {
        show(SettingsUiState())

        composeRule.onAllNodes(isSelectable()).assertCountEquals(0)
        composeRule.onAllNodesWithText(str(R.string.ok)).assertCountEquals(0)
    }
}
