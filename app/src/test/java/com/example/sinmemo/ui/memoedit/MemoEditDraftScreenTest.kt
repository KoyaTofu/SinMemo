package com.example.sinmemo.ui.memoedit

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.R
import com.example.sinmemo.data.memo.FakeMemoRepository
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.ui.theme.SinMemoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 下書きの画面側のテスト（下書き 詳細設計 2 章・3 章）。
 * Activity を本当に裏に回して（ライフサイクルを CREATED へ）、ON_STOP で書きかけが画面の状態に入ることを確かめる。
 */
@RunWith(RobolectricTestRunner::class)
class MemoEditDraftScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = FakeMemoRepository()

    private fun show(handle: SavedStateHandle): MemoEditViewModel {
        val viewModel = MemoEditViewModel(handle, repository)
        composeRule.setContent {
            SinMemoTheme { MemoEditScreen(onClose = {}, viewModel = viewModel) }
        }
        composeRule.waitForIdle()
        return viewModel
    }

    private fun body() = composeRule.onNodeWithContentDescription(context.getString(R.string.memo_body))

    @Test
    fun goingToBackground_putsDraftIntoSavedState() {
        repository.put(Memo(id = 7, body = "牛乳", createdAt = 1, updatedAt = 1))
        val handle = SavedStateHandle(mapOf(MemoEditViewModel.KEY_MEMO_ID to 7L))
        val viewModel = show(handle)
        composeRule.runOnIdle { viewModel.text.edit { append("と卵") } }
        assertNull(handle.get<String>(MemoEditViewModel.KEY_DRAFT_BODY))

        // ホームに戻るなどで画面が見えなくなる（ON_STOP）
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        assertEquals("牛乳と卵", handle.get<String>(MemoEditViewModel.KEY_DRAFT_BODY))
    }

    @Test
    fun restoredDraft_isShown_andKeyboardIsNotRequested() {
        show(SavedStateHandle(mapOf(MemoEditViewModel.KEY_DRAFT_BODY to "書きかけ")))

        composeRule.onNodeWithText("書きかけ").assertExists()
        // 新規でも、書きかけを戻したときはフォーカスを当てない（キーボードを出さない）
        body().assertIsNotFocused()
    }

    @Test
    fun newWithoutDraft_requestsFocus() {
        show(SavedStateHandle())

        // 比較用: 普通の新規はフォーカスが当たる（メモ編集 基本設計 決定事項 1）
        body().assertIsFocused()
    }
}
