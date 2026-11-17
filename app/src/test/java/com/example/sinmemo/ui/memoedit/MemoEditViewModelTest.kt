package com.example.sinmemo.ui.memoedit

import android.database.sqlite.SQLiteFullException
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import com.example.sinmemo.data.memo.FakeMemoRepository
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.data.memo.MemoRepository
import com.example.sinmemo.data.memo.MemoSummary
import com.example.sinmemo.data.memo.SavedMemo
import com.example.sinmemo.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * MemoEditViewModel の単体テスト（メモ編集 基本設計 3.2・3.3、テスト設計 1.2）。偽のリポジトリを使う。
 * Dispatchers.Main は UnconfinedTestDispatcher なので、viewModelScope.launch はその場で（止まるところまで）進む。
 */
class MemoEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMemoRepository(nowMillis = 5_000L)

    private fun newViewModel(memoId: Long? = null, repo: MemoRepository = repository) = MemoEditViewModel(
        savedStateHandle = SavedStateHandle(if (memoId == null) emptyMap() else mapOf(MemoEditViewModel.KEY_MEMO_ID to memoId)),
        repository = repo,
    )

    private fun existing(body: String = "牛乳") =
        Memo(id = 7, body = body, createdAt = 1_000L, updatedAt = 1_000L).also { repository.put(it) }

    private suspend fun allMemos(): List<MemoSummary> = repository.observeSummaries().first()

    // ---- 開いたとき（3.3） ----

    @Test
    fun new_startsEmpty_notLoading() {
        val vm = newViewModel()

        val state = vm.uiState.value
        assertTrue(state.isNew)
        assertFalse(state.isLoading)
        assertEquals("", vm.text.text.toString())
    }

    @Test
    fun memoIdZero_isNew() {
        assertTrue(newViewModel(memoId = 0L).uiState.value.isNew)
    }

    @Test
    fun existing_loadsBody() {
        existing("牛乳\n卵")

        val vm = newViewModel(memoId = 7)

        val state = vm.uiState.value
        assertFalse(state.isNew)
        assertFalse(state.isLoading)
        assertFalse(state.isClosed)
        assertEquals("牛乳\n卵", vm.text.text.toString())
    }

    @Test
    fun existing_notFound_closes() {
        val vm = newViewModel(memoId = 999)

        assertTrue(vm.uiState.value.isClosed)
    }

    // ---- ✓（3.2） ----

    @Test
    fun save_new_createsMemoAndCloses() = runTest {
        val vm = newViewModel()
        vm.text.setTextAndPlaceCursorAtEnd("買い物")

        vm.onSave()

        assertTrue(vm.uiState.value.isClosed)
        val saved = allMemos().single()
        assertEquals(Memo(saved.id, "買い物", 5_000L, 5_000L), repository.getMemo(saved.id))
    }

    @Test
    fun save_doesNothing_whenBlank() = runTest {
        val vm = newViewModel()
        for (blank in listOf("", "   ", "\n\n", "　")) {
            vm.text.setTextAndPlaceCursorAtEnd(blank)

            vm.onSave()

            assertFalse("「$blank」で閉じた", vm.uiState.value.isClosed)
        }
        assertEquals(0, allMemos().size)
    }

    @Test
    fun save_existingUnchanged_closesWithoutUpdating() = runTest {
        existing("牛乳")
        val vm = newViewModel(memoId = 7)

        vm.onSave()

        assertTrue(vm.uiState.value.isClosed)
        // 更新日時が変わらない（モックアップ検証 論点 2）
        assertEquals(1_000L, repository.getMemo(7)!!.updatedAt)
    }

    @Test
    fun save_existingChanged_updatesAndCloses() = runTest {
        existing("牛乳")
        val vm = newViewModel(memoId = 7)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳と卵")

        vm.onSave()

        assertTrue(vm.uiState.value.isClosed)
        assertEquals(Memo(7, "牛乳と卵", 1_000L, 5_000L), repository.getMemo(7))
    }

    @Test
    fun save_existing_spaceOnlyChange_isSaved() = runTest {
        // 空白・改行の違いも「変更あり」（3.1）
        existing("牛乳")
        val vm = newViewModel(memoId = 7)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳\n")

        vm.onSave()

        assertEquals("牛乳\n", repository.getMemo(7)!!.body)
    }

    @Test
    fun save_twiceWhileSaving_createsOnlyOne() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val vm = newViewModel()
        vm.text.setTextAndPlaceCursorAtEnd("1 件だけ")

        vm.onSave()
        assertTrue(vm.uiState.value.isBusy)
        vm.onSave() // 保存中の二度押し
        gate.complete(Unit)

        assertEquals(1, repository.createCalls)
        assertEquals(1, allMemos().size)
        assertTrue(vm.uiState.value.isClosed)
    }

    // ---- ← ／戻る操作と D-02 ----

    @Test
    fun back_new_untouched_closesWithoutDialog() {
        val vm = newViewModel()

        vm.onBack()

        assertTrue(vm.uiState.value.isClosed)
        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun back_new_spacesOnly_showsDiscard() {
        val vm = newViewModel()
        vm.text.setTextAndPlaceCursorAtEnd("  ")

        vm.onBack()

        assertEquals(EditDialog.Discard, vm.uiState.value.dialog)
        assertFalse(vm.uiState.value.isClosed)
    }

    @Test
    fun back_existingUnchanged_closes() {
        existing()
        val vm = newViewModel(memoId = 7)

        vm.onBack()

        assertTrue(vm.uiState.value.isClosed)
    }

    @Test
    fun back_existingChanged_showsDiscard_thenCancelKeepsEditing() {
        existing("牛乳")
        val vm = newViewModel(memoId = 7)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳と卵")

        vm.onBack()
        assertEquals(EditDialog.Discard, vm.uiState.value.dialog)

        vm.onDialogDismiss()
        assertNull(vm.uiState.value.dialog)
        assertFalse(vm.uiState.value.isClosed)
        assertEquals("牛乳と卵", vm.text.text.toString())
    }

    @Test
    fun discardConfirm_closesWithoutSaving() = runTest {
        existing("牛乳")
        val vm = newViewModel(memoId = 7)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳と卵")
        vm.onBack()

        vm.onDiscardConfirm()

        assertTrue(vm.uiState.value.isClosed)
        assertNull(vm.uiState.value.dialog)
        assertEquals(Memo(7, "牛乳", 1_000L, 1_000L), repository.getMemo(7))
    }

    // ---- 削除（D-01） ----

    @Test
    fun deleteRequest_ignoredForNew() {
        val vm = newViewModel()

        vm.onDeleteRequest()

        assertNull(vm.uiState.value.dialog)
    }

    @Test
    fun delete_cancel_keepsMemo() = runTest {
        existing()
        val vm = newViewModel(memoId = 7)

        vm.onDeleteRequest()
        assertEquals(EditDialog.Delete, vm.uiState.value.dialog)
        vm.onDialogDismiss()

        assertNull(vm.uiState.value.dialog)
        assertFalse(vm.uiState.value.isClosed)
        assertEquals(1, allMemos().size)
    }

    @Test
    fun delete_confirm_deletesAndCloses() = runTest {
        existing()
        val vm = newViewModel(memoId = 7)

        vm.onDeleteRequest()
        vm.onDeleteConfirm()

        assertTrue(vm.uiState.value.isClosed)
        assertNull(repository.getMemo(7))
    }

    // ---- 一覧へ渡す「保存したメモ」（id と更新日時。メモ一覧 詳細設計 9.3・総合テスト D-1） ----

    /** E-U-24 */
    @Test
    fun savedMemo_new_isCreatedIdAndUpdatedAt() = runTest {
        existing()
        val vm = newViewModel()
        vm.text.setTextAndPlaceCursorAtEnd("卵")

        vm.onSave()

        val created = allMemos().single { it.head == "卵" }
        assertTrue(vm.uiState.value.isClosed)
        assertEquals(SavedMemo(created.id, 5_000L), vm.uiState.value.savedMemo)
    }

    /** E-U-25 */
    @Test
    fun savedMemo_existingChanged_isThatIdAndUpdatedAt() {
        existing()
        val vm = newViewModel(memoId = 7)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳と卵")

        vm.onSave()

        assertTrue(vm.uiState.value.isClosed)
        assertEquals(SavedMemo(7L, 5_000L), vm.uiState.value.savedMemo)
    }

    /** E-U-26 */
    @Test
    fun savedMemo_isNull_whenClosedWithoutSaving() {
        existing()
        // 変更なしの ✓（保存しない。一覧は今の位置のまま）
        newViewModel(memoId = 7).run {
            onSave()
            assertTrue(uiState.value.isClosed)
            assertNull(uiState.value.savedMemo)
        }
        // 変更なしの ←
        newViewModel(memoId = 7).run {
            onBack()
            assertTrue(uiState.value.isClosed)
            assertNull(uiState.value.savedMemo)
        }
        // 破棄
        newViewModel(memoId = 7).run {
            text.setTextAndPlaceCursorAtEnd("牛乳と卵")
            onBack()
            onDiscardConfirm()
            assertTrue(uiState.value.isClosed)
            assertNull(uiState.value.savedMemo)
        }
        // 削除
        newViewModel(memoId = 7).run {
            onDeleteRequest()
            onDeleteConfirm()
            assertTrue(uiState.value.isClosed)
            assertNull(uiState.value.savedMemo)
        }
    }

    // 上限を超える入力の取り消しとメッセージは、本物の入力欄を通す MemoEditScreenTest で確かめる

    // ---- 7.4（#59）保存・削除の失敗、0 行、二度押し（メモ編集 テスト設計 1.8） ----

    private fun dbError() = SQLiteFullException("記憶装置がいっぱい（テスト）")

    /** E-U-53 */
    @Test
    fun save_new_dbError_staysOpen_keepsText() = runTest {
        val vm = newViewModel()
        vm.text.setTextAndPlaceCursorAtEnd("買い物")
        repository.failNextWith = dbError()

        vm.onSave()

        val state = vm.uiState.value
        assertFalse(state.isClosed)
        assertFalse(state.isBusy)
        assertEquals(EditFailure.SaveFailed, state.failure)
        assertEquals("買い物", vm.text.text.toString())
        assertEquals(0, allMemos().size)
    }

    /** E-U-54 */
    @Test
    fun save_dbError_thenRetry_saves() = runTest {
        val vm = newViewModel()
        vm.text.setTextAndPlaceCursorAtEnd("買い物")
        repository.failNextWith = dbError()
        vm.onSave()
        vm.onFailureShown(vm.uiState.value.failureId)
        assertNull(vm.uiState.value.failure)

        vm.onSave()

        assertTrue(vm.uiState.value.isClosed)
        assertEquals("買い物", allMemos().single().head)
        assertEquals(SavedMemo(allMemos().single().id, 5_000L), vm.uiState.value.savedMemo)
    }

    /** E-U-55 */
    @Test
    fun save_existing_dbError_isStillDirty() = runTest {
        existing("牛乳")
        val vm = newViewModel(memoId = 7)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳と卵")
        repository.failNextWith = dbError()

        vm.onSave()
        vm.onBack()

        assertEquals(EditDialog.Discard, vm.uiState.value.dialog)
        assertFalse(vm.uiState.value.isClosed)
        assertEquals("牛乳", repository.getMemo(7)!!.body)
    }

    /** E-U-56 */
    @Test
    fun delete_dbError_staysOpen_showsDeleteFailed() = runTest {
        existing()
        val vm = newViewModel(memoId = 7)
        repository.failNextWith = dbError()

        vm.onDeleteRequest()
        vm.onDeleteConfirm()

        val state = vm.uiState.value
        assertFalse(state.isClosed)
        assertFalse(state.isBusy)
        assertNull(state.dialog)
        assertEquals(EditFailure.DeleteFailed, state.failure)
        assertEquals(1, allMemos().size)
    }

    /** E-U-57 */
    @Test
    fun save_updateZeroRows_savesAsNew() = runTest {
        existing("牛乳")
        val vm = newViewModel(memoId = 7)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳と卵")
        repository.removeBeforeUpdate = true

        vm.onSave()

        val saved = allMemos().single()
        assertTrue(saved.id != 7L)
        assertEquals("牛乳と卵", saved.head)
        assertTrue(vm.uiState.value.isClosed)
        assertNull(vm.uiState.value.failure)
        assertEquals(SavedMemo(saved.id, 5_000L), vm.uiState.value.savedMemo)
    }

    /** E-U-58 */
    @Test
    fun delete_alreadyGone_closes() = runTest {
        existing()
        val vm = newViewModel(memoId = 7)
        vm.onDeleteRequest()
        repository.remove(7)

        vm.onDeleteConfirm()

        assertTrue(vm.uiState.value.isClosed)
        assertNull(vm.uiState.value.failure)
    }

    /** E-U-59 */
    @Test
    fun load_dbError_closes_evenWithDraft() = runTest {
        existing("牛乳")
        val failing = object : MemoRepository by repository {
            override suspend fun getMemo(id: Long): Memo? = throw dbError()
        }
        val vm = MemoEditViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(MemoEditViewModel.KEY_MEMO_ID to 7L, MemoEditViewModel.KEY_DRAFT_BODY to "書きかけ"),
            ),
            repository = failing,
        )

        assertTrue(vm.uiState.value.isClosed)
        // 新規に切り替えない
        assertFalse(vm.uiState.value.isNew)
        assertEquals(1, allMemos().size)
    }

    /** E-U-60 */
    @Test
    fun deleteConfirm_twice_deletesOnce() = runTest {
        existing()
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val vm = newViewModel(memoId = 7)
        vm.onDeleteRequest()

        vm.onDeleteConfirm()
        vm.onDeleteConfirm() // 二度押し
        gate.complete(Unit)

        assertEquals(1, repository.deleteCalls)
        assertTrue(vm.uiState.value.isClosed)
    }

    /**
     * E-U-61 上限を超えた既存メモは、減らしても 10,000 文字を超えている間は保存しない。
     * 編集画面に残り「10,000文字を超えるため保存できません」を出す（開発者の決定 2026-11-17）
     */
    @Test
    fun save_overLimitBody_isNotSaved() = runTest {
        existing("あ".repeat(10_050))
        val vm = newViewModel(memoId = 7)
        assertEquals(10_050, vm.text.text.length)

        vm.text.edit { delete(length - 1, length) }
        vm.onSave()

        val state = vm.uiState.value
        assertFalse(state.isClosed)
        assertFalse(state.isBusy)
        assertEquals(EditFailure.OverLimit, state.failure)
        assertEquals(10_049, vm.text.text.length)
        assertEquals(10_050, repository.getMemo(7)!!.body.length)
        assertEquals(0, repository.updateCalls)
    }

    /** E-U-75 10,000 文字まで減らせば保存できる */
    @Test
    fun save_overLimitBody_reducedToLimit_isSaved() = runTest {
        existing("あ".repeat(10_050))
        val vm = newViewModel(memoId = 7)
        vm.text.edit { delete(10_000, length) }

        vm.onSave()

        assertTrue(vm.uiState.value.isClosed)
        assertEquals(10_000, repository.getMemo(7)!!.body.length)
    }

    /** E-U-76 変えずに ✓ でも、上限を超えていれば保存せず残る。← は今までどおり（変更なしなら閉じる） */
    @Test
    fun save_overLimitUnchanged_staysOpen_backCloses() = runTest {
        existing("あ".repeat(10_050))
        val vm = newViewModel(memoId = 7)

        vm.onSave()
        assertFalse(vm.uiState.value.isClosed)
        assertEquals(EditFailure.OverLimit, vm.uiState.value.failure)

        vm.onBack()
        assertTrue(vm.uiState.value.isClosed)
        assertNull(vm.uiState.value.savedMemo)
    }

    /** E-U-77 上限の数え方は入力の上限と同じ（UTF-16 の数）。9,999 文字＋絵文字 1 つ（2）は 10,001 で保存しない */
    @Test
    fun save_overLimitByEmoji_isNotSaved() = runTest {
        existing("あ".repeat(9_999) + "😀")
        val vm = newViewModel(memoId = 7)
        vm.text.edit { replace(0, 1, "い") }

        vm.onSave()

        assertFalse(vm.uiState.value.isClosed)
        assertEquals(EditFailure.OverLimit, vm.uiState.value.failure)
    }

    /** E-U-73 同じ種類の失敗が続いても、通し番号が進む。古い番号で「出し終えた」と言っても新しい失敗は消えない */
    @Test
    fun repeatedFailure_getsNewId() = runTest {
        val vm = newViewModel()
        vm.text.setTextAndPlaceCursorAtEnd("買い物")
        repository.failNextWith = dbError()
        vm.onSave()
        val first = vm.uiState.value.failureId

        repository.failNextWith = dbError()
        vm.onSave()
        val second = vm.uiState.value.failureId

        assertEquals(first + 1, second)
        assertEquals(EditFailure.SaveFailed, vm.uiState.value.failure)
        vm.onFailureShown(first)
        assertEquals(EditFailure.SaveFailed, vm.uiState.value.failure)
        vm.onFailureShown(second)
        assertNull(vm.uiState.value.failure)
    }
}
