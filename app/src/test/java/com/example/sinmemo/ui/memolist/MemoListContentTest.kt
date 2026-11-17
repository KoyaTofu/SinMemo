package com.example.sinmemo.ui.memolist

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.R
import com.example.sinmemo.ui.theme.FontSize
import com.example.sinmemo.ui.theme.SinMemoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * S-01（MemoListContent）の UI テスト。Robolectric で PC 上で動かす（メモ一覧 テスト設計 2.3）。
 * 文字の幅を本物どおりに測るため、描画は NATIVE モードにする（既定の LEGACY だと「…」での省略が起きない）。
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MemoListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val longTitle = "牛乳、卵、パン、洗剤、トイレットペーパー、ティッシュ、電池、ゴミ袋、歯ブラシ、シャンプー"

    private val sample = MemoListUiState(
        items = listOf(
            MemoListItem(3, longTitle, "11/12 14:30"),
            MemoListItem(2, "打ち合わせメモ", "11/10 09:15"),
            MemoListItem(1, "旅行の持ち物", "2025/12/28 21:04"),
        ),
    )

    private var openedId: Long? = null
    private var createClicks = 0
    private var settingsClicks = 0

    private fun show(state: MemoListUiState) {
        composeRule.setContent {
            SinMemoTheme {
                MemoListContent(
                    uiState = state,
                    fontSize = FontSize.MEDIUM,
                    onOpenMemo = { openedId = it },
                    onCreateMemo = { createClicks++ },
                    onOpenSettings = { settingsClicks++ },
                )
            }
        }
    }

    @Test
    fun showsAppBarAndRows() {
        show(sample)

        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithText("打ち合わせメモ").assertIsDisplayed()
        composeRule.onNodeWithText("11/10 09:15").assertIsDisplayed()
        composeRule.onNodeWithText("旅行の持ち物").assertIsDisplayed()
        composeRule.onNodeWithText("2025/12/28 21:04").assertIsDisplayed()
    }

    @Test
    fun longTitle_fitsInOneLine_withEllipsis() {
        show(sample)

        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(longTitle)
            .fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)

        assertEquals(1, results.single().lineCount)
        assertTrue("省略されていない", results.single().isLineEllipsized(0))
    }

    @Test
    fun noRows_whenEmpty() {
        show(MemoListUiState())

        composeRule.onNodeWithText(context.getString(R.string.app_name)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.create_memo)).assertIsDisplayed()
        // 押せる部品は ⋮ と ＋ の 2 つだけ（行がない）
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(2)
    }

    @Test
    fun clickRow_callsOnOpenMemo_withItsId() {
        show(sample)

        composeRule.onNodeWithText("打ち合わせメモ").performClick()

        assertEquals(2L, openedId)
    }

    @Test
    fun clickFab_callsOnCreateMemo() {
        show(sample)

        composeRule.onNodeWithContentDescription(context.getString(R.string.create_memo)).performClick()

        assertEquals(1, createClicks)
    }

    @Test
    fun menuSettings_callsOnOpenSettings() {
        show(sample)
        val settings = context.getString(R.string.settings_title)
        composeRule.onAllNodesWithText(settings).assertCountEquals(0)

        composeRule.onNodeWithContentDescription(context.getString(R.string.menu)).performClick()
        composeRule.onNodeWithText(settings).performClick()

        assertEquals(1, settingsClicks)
        // メニューは閉じる
        composeRule.onAllNodesWithText(settings).assertCountEquals(0)
    }
}
