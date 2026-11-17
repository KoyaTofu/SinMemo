package com.example.sinmemo.ui.memoedit

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.R
import com.example.sinmemo.data.memo.FakeMemoRepository
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.ui.theme.SinMemoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 本物の ViewModel と入力欄をつないだ S-02 のテスト（メモ編集 基本設計 3.4・決定事項 3）。
 * `performTextInput` は IME（キーボード）から文字列をまとめて確定する操作で、貼り付けと同じく入力の制限を通る。
 * エミュレータ（Android 10）では adb からクリップボードに入れられず貼り付けを試せなかったため、ここで確かめる。
 */
@RunWith(RobolectricTestRunner::class)
class MemoEditScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val tooLong get() = context.getString(R.string.input_too_long)

    private lateinit var viewModel: MemoEditViewModel
    private var closed = 0

    /** 本文 [body] の既存メモ（id 1）を開く */
    private fun open(body: String) {
        val repository = FakeMemoRepository().apply { put(Memo(id = 1, body = body, createdAt = 1, updatedAt = 1)) }
        viewModel = MemoEditViewModel(SavedStateHandle(mapOf(MemoEditViewModel.KEY_MEMO_ID to 1L)), repository)
        composeRule.setContent {
            SinMemoTheme { MemoEditScreen(onClose = { closed++ }, viewModel = viewModel) }
        }
        composeRule.waitForIdle()
    }

    private fun body() = composeRule.onNodeWithContentDescription(context.getString(R.string.memo_body))
    private fun text() = viewModel.text.text.toString()

    @Test
    fun pasteAtEnd_overLimit_isRejected_andShowsMessage() {
        val base = "a".repeat(9_990)
        open(base)

        body().performTextInputSelection(TextRange(9_990))
        body().performTextInput("b".repeat(30))

        assertEquals(base, text())
        composeRule.onNodeWithText(tooLong).assertIsDisplayed()
    }

    @Test
    fun pasteInMiddle_overLimit_isRejected_andKeepsExistingText() {
        val base = "a".repeat(9_990)
        open(base)

        body().performTextInputSelection(TextRange(100))
        body().performTextInput("b".repeat(30))

        assertEquals(base, text())
        composeRule.onNodeWithText(tooLong).assertIsDisplayed()
    }

    @Test
    fun pasteReachingExactlyLimit_isAccepted() {
        open("a".repeat(9_990))

        body().performTextInputSelection(TextRange(9_990))
        body().performTextInput("b".repeat(10))

        assertEquals(10_000, text().length)
        assertTrue(text().endsWith("bbbbbbbbbb"))
        composeRule.onAllNodesWithText(tooLong).assertCountEquals(0)
        composeRule.onNodeWithText("10,000/10,000文字").assertIsDisplayed()
    }

    @Test
    fun typingOneCharAtLimit_isRejected_withoutMessage() {
        val base = "a".repeat(10_000)
        open(base)

        body().performTextInputSelection(TextRange(10_000))
        body().performTextInput("x")

        assertEquals(base, text())
        composeRule.onAllNodesWithText(tooLong).assertCountEquals(0)
    }

    @Test
    fun inputUnderLimit_isAccepted() {
        open("牛乳")

        body().performTextInputSelection(TextRange(2))
        body().performTextInput("と卵とパン")

        assertEquals("牛乳と卵とパン", text())
        composeRule.onAllNodesWithText(tooLong).assertCountEquals(0)
    }

    @Test
    fun message_disappears_andStateResets() {
        open("a".repeat(9_990))
        body().performTextInputSelection(TextRange(9_990))
        body().performTextInput("b".repeat(30))
        composeRule.onNodeWithText(tooLong).assertIsDisplayed()

        // Snackbar の表示時間（短い表示は 4 秒）より先へ時計を進める
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText(tooLong).assertCountEquals(0)
        assertFalse(viewModel.uiState.value.showTooLongMessage)
    }

    @Test
    fun save_callsOnCloseOnce() {
        open("牛乳")
        body().performTextInputSelection(TextRange(2))
        body().performTextInput("と卵")

        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, closed)
        assertEquals("牛乳と卵", text())
    }

    // ---- 7.4（#59）上限まわり（メモ編集 テスト設計 1.8） ----

    private fun assertNoMessage() = composeRule.onAllNodesWithText(tooLong).assertCountEquals(0)

    /** E-U-62 */
    @Test
    fun tooLongMessage_saysCannotInput() {
        open("a".repeat(9_990))
        body().performTextInputSelection(TextRange(9_990))
        body().performTextInput("b".repeat(30))

        composeRule.onNodeWithText("10,000文字を超えるため入力できません").assertIsDisplayed()
    }

    /** E-U-63 上限を超えた本文から 1 文字消せる */
    @Test
    fun overLimitBody_canDeleteOneChar() {
        open("a".repeat(10_050))

        body().performTextReplacement("a".repeat(10_049))

        assertEquals(10_049, text().length)
        assertNoMessage()
    }

    /** E-U-64 上限を超えた本文の 10 文字を 3 文字に置き換えられる */
    @Test
    fun overLimitBody_canReplaceWithShorter() {
        open("a".repeat(10_050))

        body().performTextInputSelection(TextRange(100, 110))
        body().performTextInput("xyz")

        assertEquals(10_043, text().length)
        assertEquals("xyz", text().substring(100, 103))
    }

    /** E-U-65 上限を超えた本文を増やす変更は今までどおり取り消す */
    @Test
    fun overLimitBody_rejectsGrowth() {
        val base = "a".repeat(10_050)
        open(base)
        body().performTextInputSelection(TextRange(10_050))

        body().performTextInput("x")
        assertEquals(base, text())
        assertNoMessage()

        body().performTextInput("xy")
        assertEquals(base, text())
        composeRule.onNodeWithText(tooLong).assertIsDisplayed()
    }

    /** E-U-66 9,999 文字に絵文字（UTF-16 で 2）は入らず、メッセージが出る */
    @Test
    fun emojiAt9999_isRejected_withMessage() {
        val base = "a".repeat(9_999)
        open(base)
        body().performTextInputSelection(TextRange(9_999))

        body().performTextInput("😀")

        assertEquals(base, text())
        composeRule.onNodeWithText(tooLong).assertIsDisplayed()
    }

    /** E-U-67 9,998 文字に絵文字でちょうど 10,000 になり、入る */
    @Test
    fun emojiAt9998_isAccepted() {
        open("a".repeat(9_998))
        body().performTextInputSelection(TextRange(9_998))

        body().performTextInput("😀")

        assertEquals(10_000, text().length)
        assertTrue(text().endsWith("😀"))
        assertNoMessage()
    }

    /** E-U-68 巨大な貼り付けは 1 文字も入らず、メッセージ */
    @Test
    fun hugePaste_isRejected_keepsText() {
        val base = "a".repeat(100)
        open(base)
        body().performTextInputSelection(TextRange(100))

        body().performTextInput("b".repeat(50_000))

        assertEquals(base, text())
        composeRule.onNodeWithText(tooLong).assertIsDisplayed()
    }

    /** E-U-78 上限を超えた本文で ✓ を押すと、保存せず「10,000文字を超えるため保存できません」を出して残る */
    @Test
    fun overLimitBody_save_showsMessage_andStays() {
        open("a".repeat(10_050))

        composeRule.onNodeWithContentDescription(context.getString(R.string.save)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("10,000文字を超えるため保存できません").assertIsDisplayed()
        assertEquals(0, closed)
        assertEquals(10_050, text().length)
    }
}
