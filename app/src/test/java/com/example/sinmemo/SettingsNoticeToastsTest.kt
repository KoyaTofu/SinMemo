package com.example.sinmemo

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/**
 * 設定のメッセージのトースト（設定 詳細設計 10.5、設定 テスト設計 ST-U-55）。
 * 同じ種類のメッセージが続いても（[Reset, Reset]）、通し番号で見分けて 2 つとも出す。
 */
@RunWith(RobolectricTestRunner::class)
class SettingsNoticeToastsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** ST-U-55 */
    @Test
    fun sameNoticeTwice_showsTwoToasts() {
        var notices by mutableStateOf(
            listOf(QueuedNotice(1, SettingsNotice.Reset), QueuedNotice(2, SettingsNotice.Reset)),
        )
        val shown = mutableListOf<Long>()
        composeRule.setContent {
            SettingsNoticeToasts(notices = notices) { id ->
                shown += id
                notices = notices.filterNot { it.id == id }
            }
        }
        composeRule.waitForIdle()

        assertEquals(listOf(1L, 2L), shown)
        assertEquals(2, ShadowToast.shownToastCount())
        assertEquals(context.getString(R.string.settings_reset), ShadowToast.getTextOfLatestToast())
    }
}
