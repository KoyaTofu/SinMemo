package com.example.sinmemo.ui.memoedit

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.R
import com.example.sinmemo.ui.theme.FontSize
import com.example.sinmemo.ui.theme.SinMemoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** S-02（MemoEditContent）の UI テスト。状態を決めて描き、表示と押したときの呼び出しを確かめる。 */
@RunWith(RobolectricTestRunner::class)
class MemoEditContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun s(id: Int) = context.getString(id)

    private val calls = mutableListOf<String>()

    private fun show(state: MemoEditUiState, body: String) {
        composeRule.setContent {
            SinMemoTheme {
                MemoEditContent(
                    uiState = state,
                    text = TextFieldState(body),
                    inputTransformation = null,
                    fontSize = FontSize.MEDIUM,
                    onSave = { calls += "save" },
                    onBack = { calls += "back" },
                    onDeleteRequest = { calls += "deleteRequest" },
                    onDeleteConfirm = { calls += "deleteConfirm" },
                    onDiscardConfirm = { calls += "discardConfirm" },
                    onDialogDismiss = { calls += "dismiss" },
                    onTooLongMessageShown = { calls += "messageShown" },
                    onFailureShown = { id -> calls += "failureShown:$id" },
                )
            }
        }
    }

    @Test
    fun save_isDisabled_whenBlank() {
        show(MemoEditUiState(isNew = true), "  \n")

        composeRule.onNodeWithContentDescription(s(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun save_isEnabled_andCallsOnSave_whenHasText() {
        show(MemoEditUiState(isNew = true), "買い物")

        composeRule.onNodeWithContentDescription(s(R.string.save)).assertIsEnabled().performClick()

        assertEquals(listOf("save"), calls)
    }

    @Test
    fun save_isDisabled_whileBusy() {
        show(MemoEditUiState(isNew = false, isBusy = true), "買い物")

        composeRule.onNodeWithContentDescription(s(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun placeholder_shown_whenEmpty() {
        show(MemoEditUiState(isNew = true), "")

        composeRule.onNodeWithText(s(R.string.memo_body_placeholder)).assertIsDisplayed()
    }

    @Test
    fun new_hasNoMenu() {
        show(MemoEditUiState(isNew = true), "")

        composeRule.onAllNodesWithContentDescription(s(R.string.menu)).assertCountEquals(0)
    }

    @Test
    fun existing_menuDelete_callsOnDeleteRequest() {
        show(MemoEditUiState(isNew = false), "牛乳")

        composeRule.onNodeWithContentDescription(s(R.string.menu)).performClick()
        composeRule.onNodeWithText(s(R.string.delete)).performClick()

        assertEquals(listOf("deleteRequest"), calls)
    }

    @Test
    fun backArrow_callsOnBack() {
        show(MemoEditUiState(isNew = false), "牛乳")

        composeRule.onNodeWithContentDescription(s(R.string.back)).performClick()

        assertEquals(listOf("back"), calls)
    }

    @Test
    fun deleteDialog_showsMessage_andButtons() {
        show(MemoEditUiState(isNew = false, dialog = EditDialog.Delete), "牛乳")

        composeRule.onNodeWithText(s(R.string.confirm_delete)).assertIsDisplayed()
        composeRule.onNodeWithText(s(R.string.cancel)).performClick()
        composeRule.onNodeWithText(s(R.string.delete)).performClick()

        assertEquals(listOf("dismiss", "deleteConfirm"), calls)
    }

    @Test
    fun discardDialog_showsMessage_andButtons() {
        show(MemoEditUiState(isNew = false, dialog = EditDialog.Discard), "牛乳")

        composeRule.onNodeWithText(s(R.string.confirm_discard)).assertIsDisplayed()
        composeRule.onNodeWithText(s(R.string.cancel)).performClick()
        composeRule.onNodeWithText(s(R.string.discard)).performClick()

        assertEquals(listOf("dismiss", "discardConfirm"), calls)
    }

    @Test
    fun charCount_hidden_at9499() {
        show(MemoEditUiState(isNew = false), "a".repeat(9_499))

        composeRule.onAllNodesWithText("9,499/10,000文字").assertCountEquals(0)
    }

    @Test
    fun charCount_shown_from9500() {
        show(MemoEditUiState(isNew = false), "a".repeat(9_500))

        composeRule.onNodeWithText("9,500/10,000文字").assertIsDisplayed()
    }

    @Test
    fun charCount_shownAtLimit() {
        show(MemoEditUiState(isNew = false), "a".repeat(10_000))

        composeRule.onNodeWithText("10,000/10,000文字").assertIsDisplayed()
    }

    // ---- 7.4（#59）失敗のメッセージ・上限を超えた文字数（メモ編集 テスト設計 1.8） ----

    /** E-U-69 */
    @Test
    fun saveFailed_showsMessage_andReports() {
        show(MemoEditUiState(isNew = false, failure = EditFailure.SaveFailed, failureId = 1), "牛乳")

        composeRule.onNodeWithText("保存できませんでした").assertIsDisplayed()
        // 帯（Snackbar）の表示時間より先へ時計を進めると、出し終えたことを知らせる
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        assertEquals(listOf("failureShown:1"), calls)
    }

    /** E-U-70 */
    @Test
    fun deleteFailed_showsMessage() {
        show(MemoEditUiState(isNew = false, failure = EditFailure.DeleteFailed), "牛乳")

        composeRule.onNodeWithText("削除できませんでした").assertIsDisplayed()
    }

    /** E-U-71 */
    @Test
    fun overLimitCount_isShownInError() {
        show(MemoEditUiState(isNew = false), "a".repeat(10_050))

        composeRule.onNodeWithText("10,050/10,000文字").assertIsDisplayed()
    }

    /** E-U-74 帯を出している間に同じ種類の失敗がもう一度起きても、新しく出し直し、新しい番号で知らせる */
    @Test
    fun sameFailureAgain_whileShowing_isShownAgain() {
        var state by mutableStateOf(MemoEditUiState(isNew = false, failure = EditFailure.SaveFailed, failureId = 1))
        composeRule.setContent {
            SinMemoTheme {
                MemoEditContent(
                    uiState = state,
                    text = TextFieldState("牛乳"),
                    inputTransformation = null,
                    fontSize = FontSize.MEDIUM,
                    onSave = {}, onBack = {}, onDeleteRequest = {}, onDeleteConfirm = {},
                    onDiscardConfirm = {}, onDialogDismiss = {}, onTooLongMessageShown = {},
                    onFailureShown = { id -> calls += "failureShown:$id" },
                )
            }
        }
        composeRule.onNodeWithText("保存できませんでした").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(1_000)
        state = state.copy(failureId = 2)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("保存できませんでした").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        assertEquals(listOf("failureShown:2"), calls)
    }
}
