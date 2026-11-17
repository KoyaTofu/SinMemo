package com.example.sinmemo

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 本物の MainActivity（Hilt・Room・NavHost 込み）で、保存して一覧に戻ったときだけ一覧が先頭へ戻ることを確かめる
 * （総合テストの不具合 D-1。総合テスト テスト結果 3 章、メモ一覧 基本設計 5.1、仕組みは詳細設計 7 章）。編集画面から一覧へ「保存したメモの id」を渡す部分（NavHost）も通す。
 */
@RunWith(RobolectricTestRunner::class)
class SaveScrollFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun str(id: Int) = context.getString(id)

    /** 一覧に作るメモの数。PC 上の画面（縦 470dp ほど）に入りきらない数 */
    private val count = 25

    private fun waitForNode(text: String) {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForList() = waitForNode(str(R.string.app_name))

    private fun create(body: String) {
        composeRule.onNode(hasContentDescription(str(R.string.create_memo))).performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput(body)
        composeRule.onNode(hasContentDescription(str(R.string.save))).performClick()
        waitForList()
        waitForNode(body)
    }

    /** メモ 1〜[count] を作り（先頭はメモ [count]）、一覧のいちばん下までスクロールする */
    private fun prepareScrolledList() {
        waitForList()
        (1..count).forEach { create("メモ$it") }
        scrollToBottom()
    }

    private fun scrollToBottom() {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(count - 1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("メモ1").assertIsDisplayed()
        assertNotShown("メモ$count")
    }

    /** その行が画面に見えていない（作られていないか、画面の外にある）こと */
    private fun assertNotShown(text: String) {
        // 一覧は画面のすぐ外の行も前もって作っておくことがあるので、「ない」ではなく「見えていない」で確かめる
        if (composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(text).assertIsNotDisplayed()
        }
    }

    @Test
    fun createNew_afterScrolling_showsSavedMemoAtTop() {
        prepareScrolledList()

        create("新しいメモ")

        composeRule.onNodeWithText("新しいメモ").assertIsDisplayed()
    }

    @Test
    fun updateExisting_afterScrolling_showsSavedMemoAtTop() {
        prepareScrolledList()

        composeRule.onNodeWithText("メモ1").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasSetTextAction()).performTextInput("（追記）")
        composeRule.onNode(hasContentDescription(str(R.string.save))).performClick()
        waitForList()

        waitForNode("メモ1（追記）")
        composeRule.onNodeWithText("メモ1（追記）").assertIsDisplayed()
    }

    @Test
    fun backWithoutSaving_keepsPosition() {
        prepareScrolledList()

        composeRule.onNodeWithText("メモ1").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasContentDescription(str(R.string.back))).performClick()
        waitForList()
        waitForNode("メモ1")

        composeRule.onNodeWithText("メモ1").assertIsDisplayed()
        assertNotShown("メモ$count")
    }
}
