package com.example.sinmemo

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.data.settings.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 本物の MainActivity（Hilt・スプラッシュ・DataStore 込み）で、設定画面から変えた設定が
 * 一覧・編集に反映され、Activity を作り直しても残ることを確かめる（設定 テスト設計 1 章）。
 */
@RunWith(RobolectricTestRunner::class)
class SettingsFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun str(id: Int) = context.getString(id)

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun textSize(text: String): TextUnit {
        val results = mutableListOf<TextLayoutResult>()
        // 行全体が押せるので、文字は行にまとめられている。1 つずつの Text を見るため、まとめる前の木を使う
        composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single().layoutInput.style.fontSize
    }

    private fun openSettings() {
        composeRule.onNode(hasContentDescription(str(R.string.menu))).performClick()
        composeRule.onNodeWithText(str(R.string.settings_title)).performClick()
        waitForText(str(R.string.version))
    }

    /**
     * 項目を押し、[option] を選んで「OK」。保存は DataStore が別のスレッドでファイルに書くので、
     * waitForIdle（画面が落ち着くまで）では足りない。ダイアログが閉じて、項目の値が [option] になるまで待つ。
     */
    private fun choose(item: Int, option: String) {
        composeRule.onNodeWithText(str(item)).performClick()
        composeRule.onNode(isSelectable() and hasText(option)).performClick()
        composeRule.onNodeWithText(str(R.string.ok)).performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(isSelectable()).fetchSemanticsNodes().isEmpty() &&
                composeRule.onAllNodes(hasText(option)).fetchSemanticsNodes().size == 1
        }
    }

    private fun back() {
        composeRule.onNode(hasContentDescription(str(R.string.back))).performClick()
        waitForText(str(R.string.app_name))
    }

    @Test
    fun settingsScreen_showsDefaults_andVersionName() {
        waitForText(str(R.string.app_name))
        openSettings()

        composeRule.onNodeWithText("ライト").fetchSemanticsNode()
        composeRule.onNodeWithText("中").fetchSemanticsNode()
        composeRule.onNodeWithText(BuildConfig.VERSION_NAME).fetchSemanticsNode()
        // 単体テストはデバッグ版で動くので、後ろに -debug が付く（リリース 基本設計 6.1・6.5。RL-U-03）
        assertEquals("1.0.0-debug", BuildConfig.VERSION_NAME)
    }

    @Test
    fun fontSize_isAppliedToEditScreen_andSurvivesRecreate() {
        waitForText(str(R.string.app_name))
        openSettings()
        choose(R.string.font_size, "大")
        choose(R.string.theme, "ダーク")
        back()

        // 新規作成の本文が「大」（20sp）
        composeRule.onNode(hasContentDescription(str(R.string.create_memo))).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("牛乳")
        waitForText("牛乳")
        assertEquals(20.sp, textSize("牛乳"))

        // 画面の回転などで Activity を作り直しても、設定は残る
        composeRule.activityRule.scenario.recreate()
        waitForText("牛乳")
        assertEquals(20.sp, textSize("牛乳"))
        // テーマ「ダーク」も残っている（作り直した Activity の MainViewModel が読んだ値）
        val viewModel = ViewModelProvider(composeRule.activity)[MainViewModel::class.java]
        assertEquals(ThemeMode.DARK, viewModel.settings.value?.themeMode)
    }
}
