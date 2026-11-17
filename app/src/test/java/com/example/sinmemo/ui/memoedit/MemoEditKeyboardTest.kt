package com.example.sinmemo.ui.memoedit

import android.content.Context
import android.view.View
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.R
import com.example.sinmemo.data.memo.FakeMemoRepository
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.data.memo.SavedMemo
import com.example.sinmemo.ui.theme.SinMemoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * S-02 とキーボードのテスト（総合テストの不具合 D-2・D-3。総合テスト テスト結果 3 章、メモ編集 基本設計 3.6・決定事項 4・5）。
 * - D-2: キーボードが出ているとき、上限のメッセージ（Snackbar）がキーボードの上に出る
 * - D-3: 一覧へ戻るときは、戻る前にキーボードを閉じる（入力欄のフォーカスを外し、閉じる指示を出す）
 *
 * PC 上ではキーボードは出ないので、D-2 はキーボードの高さ（ime の余白）を画面に直接送って確かめ、
 * D-3 は「閉じる指示」を数える偽物のキーボード操作を画面に渡して確かめる。本物のキーボードでの見え方は実機で確かめる（総合テスト テスト設計 5.5 の K-4・K-5）。
 */
@RunWith(RobolectricTestRunner::class)
class MemoEditKeyboardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun str(id: Int) = context.getString(id)

    private val repository = FakeMemoRepository(nowMillis = 5_000L)
    private lateinit var viewModel: MemoEditViewModel
    private lateinit var view: View

    /** 閉じる指示（hide）の回数。onClose が呼ばれた時点の回数も残す */
    private var hides = 0
    private var hidesAtClose: Int? = null
    private var closedWith: SavedMemo? = null
    private var closeCount = 0

    private val keyboard = object : SoftwareKeyboardController {
        override fun show() = Unit
        override fun hide() {
            hides++
        }
    }

    /** [memoId] のメモ（null なら新規）を開く。既存は id 7・本文 [body] */
    private fun open(memoId: Long? = 7, body: String = "牛乳") {
        repository.put(Memo(id = 7, body = body, createdAt = 1_000L, updatedAt = 1_000L))
        val args = if (memoId == null) emptyMap() else mapOf(MemoEditViewModel.KEY_MEMO_ID to memoId)
        viewModel = MemoEditViewModel(SavedStateHandle(args), repository)
        composeRule.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalSoftwareKeyboardController provides keyboard) {
                SinMemoTheme {
                    MemoEditScreen(
                        onClose = { saved ->
                            closeCount++
                            closedWith = saved
                            hidesAtClose = hides
                        },
                        viewModel = viewModel,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun body() = composeRule.onNodeWithContentDescription(str(R.string.memo_body))

    /** 入力欄を押してフォーカスを当てる（実機ではここでキーボードが出る） */
    private fun focusBody() {
        body().performClick()
        body().assertIsFocused()
    }

    /** onClose が 1 回だけ呼ばれ、その前にキーボードを閉じる指示が出て、入力欄からフォーカスが外れている */
    private fun assertKeyboardClosedBeforeClose() {
        composeRule.waitForIdle()
        assertEquals(1, closeCount)
        assertTrue("onClose より前に hide が呼ばれていない", (hidesAtClose ?: 0) >= 1)
        body().assertIsNotFocused()
    }

    // ---- D-3: 一覧へ戻る前にキーボードを閉じる ----

    @Test
    fun save_closesKeyboard_thenPassesSavedId() {
        open()
        focusBody()
        body().performTextInputSelection(TextRange(2))
        body().performTextInput("と卵")

        composeRule.onNodeWithContentDescription(str(R.string.save)).performClick()

        assertKeyboardClosedBeforeClose()
        assertEquals(SavedMemo(7L, 5_000L), closedWith)
    }

    @Test
    fun saveNew_closesKeyboard_thenPassesCreatedId() {
        open(memoId = null)
        // 新規は開くと入力欄にフォーカスが当たる（メモ編集 基本設計 決定事項 1）
        body().assertIsFocused()
        body().performTextInput("卵")

        composeRule.onNodeWithContentDescription(str(R.string.save)).performClick()

        assertKeyboardClosedBeforeClose()
        assertEquals(SavedMemo(8L, 5_000L), closedWith)
    }

    @Test
    fun backUnchanged_closesKeyboard_withoutSavedId() {
        open()
        focusBody()

        composeRule.onNodeWithContentDescription(str(R.string.back)).performClick()

        assertKeyboardClosedBeforeClose()
        assertNull(closedWith)
    }

    @Test
    fun saveUnchanged_closesKeyboard_withoutSavedId() {
        open()
        focusBody()

        composeRule.onNodeWithContentDescription(str(R.string.save)).performClick()

        assertKeyboardClosedBeforeClose()
        assertNull(closedWith)
    }

    @Test
    fun discard_closesKeyboard_withoutSavedId() {
        open()
        focusBody()
        body().performTextInputSelection(TextRange(2))
        body().performTextInput("と卵")

        composeRule.onNodeWithContentDescription(str(R.string.back)).performClick()
        composeRule.onNodeWithText(str(R.string.discard)).performClick()

        assertKeyboardClosedBeforeClose()
        assertNull(closedWith)
    }

    @Test
    fun delete_closesKeyboard_withoutSavedId() {
        open()
        focusBody()

        composeRule.onNodeWithContentDescription(str(R.string.menu)).performClick()
        composeRule.onNodeWithText(str(R.string.delete)).performClick()
        // ダイアログの「削除」（メニューの「削除」は閉じている）
        composeRule.onNodeWithText(str(R.string.delete)).performClick()

        assertKeyboardClosedBeforeClose()
        assertNull(closedWith)
    }

    // ---- D-2: キーボードが出ているとき、上限のメッセージはその上に出る ----

    /** キーボードが画面の下から [heightPx] の高さで出ている、と画面に知らせる */
    private fun showKeyboardInsets(heightPx: Int) {
        val ime = WindowInsetsCompat.Type.ime()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(ime, Insets.of(0, 0, 0, heightPx))
            .setVisible(ime, true)
            .build()
        composeRule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        composeRule.waitForIdle()
    }

    private fun Int.pxToDp(): Dp = (this / context.resources.displayMetrics.density).dp

    @Test
    fun tooLongMessage_isAboveKeyboard() {
        open(body = "a".repeat(9_990))
        focusBody()
        val keyboardHeight = 300
        showKeyboardInsets(keyboardHeight)
        val keyboardTop = composeRule.onRoot().getBoundsInRoot().bottom - keyboardHeight.pxToDp()

        body().performTextInputSelection(TextRange(9_990))
        body().performTextInput("b".repeat(30))

        val message = composeRule.onNodeWithText(str(R.string.input_too_long)).getBoundsInRoot()
        assertTrue("メッセージの下端 ${message.bottom} がキーボードの上端 $keyboardTop より下", message.bottom <= keyboardTop)
        // 入力欄と文字数の行も、これまでどおりキーボードの上
        assertTrue(body().getBoundsInRoot().bottom <= keyboardTop)
        val count = composeRule.onNodeWithText("9,990/10,000文字").getBoundsInRoot()
        assertTrue(count.bottom <= keyboardTop)
    }

    /** E-U-72 保存に失敗したときのメッセージも、キーボードの上に出る */
    @Test
    fun failureMessage_isAboveKeyboard() {
        open()
        focusBody()
        val keyboardHeight = 300
        showKeyboardInsets(keyboardHeight)
        val keyboardTop = composeRule.onRoot().getBoundsInRoot().bottom - keyboardHeight.pxToDp()
        body().performTextInputSelection(TextRange(2))
        body().performTextInput("と卵")
        repository.failNextWith = IllegalStateException("記憶装置がいっぱい（テスト）")

        composeRule.onNodeWithContentDescription(str(R.string.save)).performClick()

        val message = composeRule.onNodeWithText(str(R.string.save_failed)).getBoundsInRoot()
        assertTrue("メッセージの下端 ${message.bottom} がキーボードの上端 $keyboardTop より下", message.bottom <= keyboardTop)
        assertEquals(0, closeCount)
    }
}
