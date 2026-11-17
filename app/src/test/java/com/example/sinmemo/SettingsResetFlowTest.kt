package com.example.sinmemo

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/**
 * 起動する前に設定のファイルを壊しておくと、初期値に戻して「設定を初期値に戻しました」を一度だけ出す
 * （設定 テスト設計 1.3 の ST-U-54、設定 詳細設計 10.2・10.5）。本物の MainActivity（Hilt・DataStore 込み）。
 * ファイルを壊してから Activity を起動するため、SettingsFlowTest（起動してからテストが始まる）とは分けた。
 */
@RunWith(RobolectricTestRunner::class)
class SettingsResetFlowTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun str(id: Int) = context.getString(id)

    private fun waitForText(text: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** ST-U-54 */
    @Test
    fun corruptedFileAtLaunch_showsResetToastOnce() {
        File(context.filesDir, "datastore/settings.preferences_pb").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x04))
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(str(R.string.app_name))
            composeRule.waitUntil(5_000) { ShadowToast.shownToastCount() >= 1 }
            assertEquals(1, ShadowToast.shownToastCount())
            assertEquals(str(R.string.settings_reset), ShadowToast.getTextOfLatestToast())

            // 画面の回転などで Activity を作り直しても、2 回目は出ない
            scenario.recreate()
            waitForText(str(R.string.app_name))
            composeRule.waitForIdle()
            assertEquals(1, ShadowToast.shownToastCount())
        }
    }
}
