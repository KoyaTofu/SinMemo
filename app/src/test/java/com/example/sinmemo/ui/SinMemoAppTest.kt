package com.example.sinmemo.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.ui.memolist.MemoListContent
import com.example.sinmemo.ui.memolist.MemoListItem
import com.example.sinmemo.ui.memolist.MemoListUiState
import com.example.sinmemo.ui.theme.FontSize
import com.example.sinmemo.ui.theme.LocalFontSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * SinMemoApp がテーマと文字サイズを反映すること（設定 基本設計 4 章、テスト設計 1 章）。
 * NavHost の代わりに一覧の中身（MemoListContent）を入れ、MemoListScreen と同じく LocalFontSize を渡す。
 */
@RunWith(RobolectricTestRunner::class)
class SinMemoAppTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var background: Color? = null
    private var fontSize: FontSize? = null

    private fun show(darkTheme: Boolean, setting: FontSizeSetting) {
        composeRule.setContent {
            SinMemoApp(darkTheme = darkTheme, fontSize = setting) {
                background = MaterialTheme.colorScheme.background
                fontSize = LocalFontSize.current
                MemoListContent(
                    uiState = MemoListUiState(listOf(MemoListItem(1, "牛乳", "11/12 14:30"))),
                    fontSize = LocalFontSize.current,
                    onOpenMemo = {}, onCreateMemo = {}, onOpenSettings = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun textSize(text: String): TextUnit {
        val results = mutableListOf<TextLayoutResult>()
        // 行全体が押せるので、文字は行にまとめられている。1 つずつの Text を見るため、まとめる前の木を使う
        composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single().layoutInput.style.fontSize
    }

    @Test
    fun light_usesLightBackground() {
        show(darkTheme = false, setting = FontSizeSetting.MEDIUM)

        assertEquals(Color(0xFFFFFFFF), background)
    }

    @Test
    fun dark_usesDarkBackground() {
        show(darkTheme = true, setting = FontSizeSetting.MEDIUM)

        assertEquals(Color(0xFF141518), background)
    }

    @Test
    fun large_listTitleIs20sp_dateIs15sp() {
        show(darkTheme = false, setting = FontSizeSetting.LARGE)

        assertEquals(FontSize.LARGE, fontSize)
        assertEquals(20.sp, textSize("牛乳"))
        assertEquals(15.sp, textSize("11/12 14:30"))
    }

    @Test
    fun small_listTitleIs15sp() {
        show(darkTheme = false, setting = FontSizeSetting.SMALL)

        assertEquals(15.sp, textSize("牛乳"))
        assertEquals(12.sp, textSize("11/12 14:30"))
    }
}
