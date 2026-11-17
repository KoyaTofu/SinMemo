package com.example.sinmemo.ui.memoedit

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import com.example.sinmemo.data.memo.FakeMemoRepository
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.data.memo.MemoRepository
import com.example.sinmemo.data.memo.MemoSummary
import com.example.sinmemo.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * 下書き（下書き 詳細設計 1〜3 章）の ViewModel のテスト。
 * 「システムにアプリを終了されて戻る」を、SavedStateHandle の中身を新しい SavedStateHandle に移して
 * ViewModel を作り直すことで再現する（Android も同じように保存した中身を渡して作り直す）。
 */
class MemoEditDraftTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeMemoRepository(nowMillis = 5_000L)

    private fun handle(memoId: Long?, draft: String? = null) = SavedStateHandle(
        buildMap {
            if (memoId != null) put(MemoEditViewModel.KEY_MEMO_ID, memoId)
            if (draft != null) put(MemoEditViewModel.KEY_DRAFT_BODY, draft)
        },
    )

    private fun vm(handle: SavedStateHandle, repo: MemoRepository = repository) = MemoEditViewModel(handle, repo)

    /** システムに終了されて戻ったときの作り直し（保存されていた中身だけを引き継ぐ） */
    private fun recreate(old: SavedStateHandle): MemoEditViewModel =
        vm(SavedStateHandle(old.keys().associateWith { old.get<Any>(it) }))

    private fun existing(body: String = "牛乳") =
        Memo(id = 7, body = body, createdAt = 1_000L, updatedAt = 1_000L).also { repository.put(it) }

    // ---- onStop で書く（3.1） ----

    @Test
    fun onStop_putsCurrentBody_intoSavedState() {
        existing("牛乳")
        val h = handle(7)
        val vm = vm(h)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳と卵")

        vm.onStop()

        assertEquals("牛乳と卵", h.get<String>(MemoEditViewModel.KEY_DRAFT_BODY))
    }

    @Test
    fun onStop_writesEvenWithoutChanges() {
        existing("牛乳")
        val h = handle(7)

        vm(h).onStop()

        assertEquals("牛乳", h.get<String>(MemoEditViewModel.KEY_DRAFT_BODY))
    }

    @Test
    fun onStop_doesNothing_whileLoading() {
        val gate = CompletableDeferred<Unit>()
        existing()
        val h = handle(7)
        val vm = vm(h, SlowGetRepository(repository, gate))
        assertTrue(vm.uiState.value.isLoading)

        vm.onStop()

        assertNull(h.get<String>(MemoEditViewModel.KEY_DRAFT_BODY))
        gate.complete(Unit)
    }

    @Test
    fun onStop_doesNothing_afterClosed() {
        val h = handle(null)
        val vm = vm(h)
        vm.onBack() // 未入力なので閉じる
        assertTrue(vm.uiState.value.isClosed)

        vm.onStop()

        assertNull(h.get<String>(MemoEditViewModel.KEY_DRAFT_BODY))
    }

    // ---- 戻したとき（3.2） ----

    @Test
    fun noDraft_isNotRestored() {
        existing("牛乳")
        val vm = vm(handle(7))

        assertFalse(vm.uiState.value.isRestored)
        assertEquals("牛乳", vm.text.text.toString())
    }

    @Test
    fun recreate_existing_restoresDraft_andIsDirty() {
        existing("牛乳")
        val h = handle(7)
        val before = vm(h)
        before.text.setTextAndPlaceCursorAtEnd("牛乳と卵")
        before.onStop()

        val after = recreate(h)

        assertEquals("牛乳と卵", after.text.text.toString())
        assertTrue(after.uiState.value.isRestored)
        assertFalse(after.uiState.value.isNew)
        // 元の本文は DB のままなので「変更あり」→ 戻ると D-02
        after.onBack()
        assertEquals(EditDialog.Discard, after.uiState.value.dialog)
    }

    @Test
    fun recreate_existing_saveWritesDraftBody() = runTest {
        existing("牛乳")
        val h = handle(7)
        vm(h).apply { text.setTextAndPlaceCursorAtEnd("牛乳と卵"); onStop() }

        val after = recreate(h)
        after.onSave()

        assertTrue(after.uiState.value.isClosed)
        assertEquals(Memo(7, "牛乳と卵", 1_000L, 5_000L), repository.getMemo(7))
    }

    @Test
    fun recreate_new_restoresDraft() = runTest {
        val h = handle(null)
        vm(h).apply { text.setTextAndPlaceCursorAtEnd("書きかけ"); onStop() }

        val after = recreate(h)

        assertEquals("書きかけ", after.text.text.toString())
        assertTrue(after.uiState.value.isNew)
        assertTrue(after.uiState.value.isRestored)
        after.onSave()
        assertEquals(listOf("書きかけ"), repository.observeSummaries().first().map { it.head })
    }

    @Test
    fun recreate_withoutChanges_isNotDirty() {
        existing("牛乳")
        val h = handle(7)
        vm(h).onStop()

        val after = recreate(h)
        after.onBack()

        // 下書き = 元の本文なので確認なしで閉じる
        assertTrue(after.uiState.value.isClosed)
        assertNull(after.uiState.value.dialog)
    }

    @Test
    fun recreate_originalMemoDeleted_opensAsNewWithDraft() = runTest {
        existing("牛乳")
        val h = handle(7)
        vm(h).apply { text.setTextAndPlaceCursorAtEnd("牛乳と卵"); onStop() }
        repository.delete(7) // 裏に回っている間に消えた

        val after = recreate(h)

        assertFalse(after.uiState.value.isClosed)
        assertTrue(after.uiState.value.isNew)
        assertEquals("牛乳と卵", after.text.text.toString())
        // 新規なので削除は出せず、✓ で新しいメモとして保存される（下書き 基本設計 決定事項 3）
        after.onDeleteRequest()
        assertNull(after.uiState.value.dialog)
        after.onSave()
        val saved = repository.observeSummaries().first().single()
        assertEquals("牛乳と卵", saved.head)
        assertTrue(saved.id != 7L)
    }

    @Test
    fun originalMemoMissing_withoutDraft_closes() {
        assertTrue(vm(handle(7)).uiState.value.isClosed)
    }

    // ---- 7.4（#59）保存・削除を始めたら書きかけを捨てる（下書き テスト設計 1.4、下書き 詳細設計 6 章） ----

    /** DR-U-15 */
    @Test
    fun save_removesDraft_beforeDbWrite() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val h = handle(null, draft = "前に残した書きかけ")
        val vm = vm(h)
        vm.text.setTextAndPlaceCursorAtEnd("買い物")

        vm.onSave()

        // DB の書き込みが終わる前（gate が閉じている間）に、書きかけはもうない
        assertTrue(vm.uiState.value.isBusy)
        assertFalse(h.contains(MemoEditViewModel.KEY_DRAFT_BODY))
        gate.complete(Unit)
        assertTrue(vm.uiState.value.isClosed)
    }

    /** DR-U-16 */
    @Test
    fun delete_removesDraft_beforeDbWrite() = runTest {
        existing("牛乳")
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val h = handle(7, draft = "牛乳と卵")
        val vm = vm(h)

        vm.onDeleteRequest()
        vm.onDeleteConfirm()

        assertTrue(vm.uiState.value.isBusy)
        assertFalse(h.contains(MemoEditViewModel.KEY_DRAFT_BODY))
        gate.complete(Unit)
        assertTrue(vm.uiState.value.isClosed)
    }

    /** DR-U-17 */
    @Test
    fun onStop_whileBusy_doesNotWriteDraft() = runTest {
        repository.gate = CompletableDeferred()
        val h = handle(null)
        val vm = vm(h)
        vm.text.setTextAndPlaceCursorAtEnd("買い物")
        vm.onSave()
        assertTrue(vm.uiState.value.isBusy)

        vm.onStop()

        assertFalse(h.contains(MemoEditViewModel.KEY_DRAFT_BODY))
    }

    /** Android が裏に回ったとき（ON_STOP の後）に保存する、画面の状態のその時点の写し */
    private fun snapshot(h: SavedStateHandle): Map<String, Any?> = h.keys().associateWith { h.get<Any>(it) }

    /**
     * DR-U-18 保存の途中で裏に回り（そこで画面の状態が保存され）、そのあとに保存が終わってから終了された。
     * 保存された写しから作り直すと、すぐ一覧へ戻る（印は DB に書く前に付いている）
     */
    @Test
    fun save_success_marksClosed_andRecreateClosesImmediately() = runTest {
        existing("牛乳")
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val h = handle(7, draft = "牛乳")
        val vm = vm(h)
        vm.text.setTextAndPlaceCursorAtEnd("牛乳と卵")
        vm.onSave()
        assertTrue(vm.uiState.value.isBusy)

        // 書き込みの途中で裏に回った。Android はこの時点の中身を保存する（このあとはもう保存しない）
        vm.onStop()
        val saved = snapshot(h)
        gate.complete(Unit)
        assertTrue(vm.uiState.value.isClosed)
        assertEquals("牛乳と卵", repository.getMemo(7)!!.body)
        val callsBefore = repository.getMemoCalls

        // 一覧へ戻る前に終了され、保存された写しから戻った
        val after = vm(SavedStateHandle(saved))

        assertEquals(true, saved[MemoEditViewModel.KEY_CLOSED])
        assertFalse(saved.containsKey(MemoEditViewModel.KEY_DRAFT_BODY))
        assertTrue(after.uiState.value.isClosed)
        assertNull(after.uiState.value.savedMemo)
        assertEquals("読み込みをした", callsBefore, repository.getMemoCalls)
        assertEquals("", after.text.text.toString())
    }

    /** DR-U-22 削除でも同じ（書き込みの途中で保存された写しから戻ると、すぐ一覧へ） */
    @Test
    fun delete_inProgressSnapshot_recreateClosesImmediately() = runTest {
        existing("牛乳")
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        val h = handle(7)
        val vm = vm(h)
        vm.onDeleteRequest()
        vm.onDeleteConfirm()

        val saved = snapshot(h)
        gate.complete(Unit)
        val after = vm(SavedStateHandle(saved))

        assertTrue(after.uiState.value.isClosed)
        assertNull(repository.getMemo(7))
    }

    /** DR-U-23 失敗したら印は外れる。そのあと裏に回って保存された写しからは、書きかけの入った編集画面に戻る */
    @Test
    fun save_failure_removesClosedMark_thenRecreateRestoresDraft() = runTest {
        val h = handle(null)
        val vm = vm(h)
        vm.text.setTextAndPlaceCursorAtEnd("今の本文")
        repository.failNextWith = IllegalStateException("記憶装置がいっぱい（テスト）")
        vm.onSave()

        vm.onStop()
        val after = vm(SavedStateHandle(snapshot(h)))

        assertFalse(after.uiState.value.isClosed)
        assertEquals("今の本文", after.text.text.toString())
    }

    /** DR-U-19 */
    @Test
    fun save_failure_putsDraftBack() = runTest {
        val h = handle(null, draft = "前に残した書きかけ")
        val vm = vm(h)
        vm.text.setTextAndPlaceCursorAtEnd("今の本文")
        repository.failNextWith = IllegalStateException("記憶装置がいっぱい（テスト）")

        vm.onSave()

        assertEquals("今の本文", h.get<String>(MemoEditViewModel.KEY_DRAFT_BODY))
        assertFalse(h.contains(MemoEditViewModel.KEY_CLOSED))
        assertFalse(vm.uiState.value.isClosed)
    }

    /** DR-U-20 */
    @Test
    fun recreate_draftOf10000Chars_restoresAll() = runTest {
        val body = "あ".repeat(9_999) + "末"
        val h = handle(null)
        vm(h).apply { text.setTextAndPlaceCursorAtEnd(body); onStop() }

        val after = recreate(h)

        assertEquals(body, after.text.text.toString())
        after.onSave()
        assertTrue(after.uiState.value.isClosed)
        assertEquals(10_000, repository.getMemo(repository.observeSummaries().first().single().id)!!.body.length)
    }

    /** DR-U-21 */
    @Test
    fun recreate_draftOverLimit_restoresAll() = runTest {
        val body = "あ".repeat(10_049) + "末"
        existing(body)
        val h = handle(7)
        vm(h).onStop()

        val after = recreate(h)

        assertEquals(10_050, after.text.text.length)
        assertEquals(body, after.text.text.toString())
    }
}

/** getMemo を [gate] が開くまで止める（読み込み中の状態を作るため） */
private class SlowGetRepository(
    private val inner: MemoRepository,
    private val gate: CompletableDeferred<Unit>,
) : MemoRepository by inner {
    override suspend fun getMemo(id: Long): Memo? {
        gate.await()
        return inner.getMemo(id)
    }
    override fun observeSummaries(): Flow<List<MemoSummary>> = inner.observeSummaries()
}
