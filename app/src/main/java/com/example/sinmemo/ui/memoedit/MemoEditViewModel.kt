package com.example.sinmemo.ui.memoedit

import android.util.Log
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sinmemo.data.memo.MemoRepository
import com.example.sinmemo.data.memo.NEW_MEMO_ID
import com.example.sinmemo.data.memo.SavedMemo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** S-02 の状態と操作（メモ編集 基本設計 3 章・詳細設計 3 章・9 章、下書き 詳細設計 1〜3 章）。 */
@HiltViewModel
class MemoEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: MemoRepository,
) : ViewModel() {

    /**
     * 編集中のメモの id。MemoEditRoute の引数 memoId（型安全なルートは引数をこの名前で SavedStateHandle に入れる）。
     * 下書きを戻すときに元のメモが見つからなければ、新規（[NEW_MEMO_ID]）に切り替える（下書き 基本設計 決定事項 3）。
     */
    private var memoId: Long = savedStateHandle.get<Long>(KEY_MEMO_ID) ?: NEW_MEMO_ID
    private val isNew: Boolean get() = memoId == NEW_MEMO_ID

    /** 元の本文（既存メモは開いたときの本文、新規は空文字）。「変更あり」の判定に使う */
    private var original = ""

    /**
     * システムに終了される前に [onStop] で残した書きかけ（下書き 詳細設計 3 章）。
     * 最初から起動したときは画面の状態そのものがないので null になる。
     */
    private val draft: String? = savedStateHandle.get<String>(KEY_DRAFT_BODY)

    /** 本文。画面回転でも消えないよう ViewModel が持つ */
    val text = TextFieldState()

    /** 上限を超える入力を取り消す。2 文字以上を取り消したらメッセージを出す */
    val inputTransformation = MaxLengthTransformation {
        _uiState.update { it.copy(showTooLongMessage = true) }
    }

    /**
     * 保存・削除が終わった印（下書き 詳細設計 6 章）。終わってから一覧へ戻るまでの間にシステムに終了され、
     * そこから戻ったときは、編集画面を出さずにすぐ一覧へ戻る。
     */
    private val closedBefore: Boolean = savedStateHandle.get<Boolean>(KEY_CLOSED) == true

    private val _uiState = MutableStateFlow(
        if (closedBefore) {
            MemoEditUiState(isNew = isNew, isClosed = true)
        } else {
            MemoEditUiState(isLoading = !isNew, isNew = isNew, isRestored = draft != null)
        },
    )
    val uiState: StateFlow<MemoEditUiState> = _uiState.asStateFlow()

    init {
        when {
            // 終わった印があれば、読み込みも書きかけの復元もしない
            closedBefore -> Unit
            isNew -> draft?.let { text.setTextAndPlaceCursorAtEnd(it) }
            else -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            val memo = try {
                repository.getMemo(memoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 読み込みの失敗は一覧へ戻る。書きかけがあっても新規に切り替えない（元のメモが残っていると
                // 同じ内容が 2 件になるため。メモ編集 詳細設計 9.5）
                Log.w(TAG, "メモを読めなかった: ${e::class.simpleName}")
                _uiState.update { it.copy(isClosed = true) }
                return@launch
            }
            when {
                memo != null -> {
                    original = memo.body
                    text.setTextAndPlaceCursorAtEnd(draft ?: memo.body)
                    _uiState.update { it.copy(isLoading = false) }
                }
                draft != null -> {
                    // 元のメモが消えていたら、書きかけで新規作成として開く（下書き 基本設計 決定事項 3）
                    memoId = NEW_MEMO_ID
                    original = ""
                    text.setTextAndPlaceCursorAtEnd(draft)
                    _uiState.update { it.copy(isLoading = false, isNew = true) }
                }
                else -> {
                    // 見つからなければ何も出さずに一覧へ（メモ編集 基本設計 3.3）
                    _uiState.update { it.copy(isClosed = true) }
                }
            }
        }
    }

    private val body: String get() = text.text.toString()
    private val isDirty: Boolean get() = body != original

    /**
     * ✓（メモ編集 基本設計 3.2、詳細設計 9.2・9.3）。
     * 処理中にする → 書きかけを捨て、終わった印を付ける → DB → 成功なら閉じる／失敗なら印を外し、書きかけを戻して処理中を解く。
     */
    fun onSave() {
        val state = _uiState.value
        if (state.isLoading || state.isBusy || state.isClosed || MemoTextLimit.isBlank(body)) return
        val body = body
        if (MemoTextLimit.isOverLimit(body)) {
            // 上限を超えた本文は保存しない。編集画面に残り、「10,000文字を超えるため保存できません」を出す
            // （メモ編集 基本設計 5.3、開発者の決定 2026-11-17）。DB にも画面の状態にも触らない
            _uiState.update { it.copy(failure = EditFailure.OverLimit, failureId = it.failureId + 1) }
            return
        }
        if (!isNew && body == original) {
            // 変更なし: 保存せず（更新日時を変えず）一覧へ（モックアップ検証 論点 2）
            close()
            return
        }
        _uiState.update { it.copy(isBusy = true) }
        startWriting()
        val id = memoId
        viewModelScope.launch {
            try {
                val saved = if (id == NEW_MEMO_ID) {
                    repository.create(body)
                } else {
                    // メモがほかで消えていた（0 行）ら、新しいメモとして保存する（9.3）
                    repository.update(id, body) ?: repository.create(body).also { created ->
                        memoId = created.id
                        original = body
                    }
                }
                finish(savedMemo = saved)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(EditFailure.SaveFailed, body, e)
            }
        }
    }

    /** ← ／ Android の戻る操作 */
    fun onBack() {
        val state = _uiState.value
        if (state.isBusy || state.isClosed) return
        if (state.isLoading || !isDirty) close() else _uiState.update { it.copy(dialog = EditDialog.Discard) }
    }

    /** ⋮ →「削除」。既存メモだけ */
    fun onDeleteRequest() {
        val state = _uiState.value
        if (isNew || state.isLoading || state.isBusy) return
        _uiState.update { it.copy(dialog = EditDialog.Delete) }
    }

    /** D-01「削除」（メモ編集 詳細設計 9.4）。もう消えていても（0 行）成功として閉じる */
    fun onDeleteConfirm() {
        val state = _uiState.value
        if (state.isBusy || state.isClosed) return
        _uiState.update { it.copy(dialog = null, isBusy = true) }
        startWriting()
        val id = memoId
        val body = body
        viewModelScope.launch {
            try {
                repository.delete(id)
                finish(savedMemo = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(EditFailure.DeleteFailed, body, e)
            }
        }
    }

    /** D-02「破棄」 */
    fun onDiscardConfirm() {
        _uiState.update { it.copy(dialog = null) }
        close()
    }

    /** ダイアログの「キャンセル」／外を押す／戻る操作 */
    fun onDialogDismiss() {
        _uiState.update { it.copy(dialog = null) }
    }

    /** 上限のメッセージを出し終えた */
    fun onTooLongMessageShown() {
        _uiState.update { it.copy(showTooLongMessage = false) }
    }

    /** 通し番号 [failureId] の失敗のメッセージを出し終えた。そのあとに新しい失敗が起きていれば消さない */
    fun onFailureShown(failureId: Int) {
        _uiState.update { if (it.failureId == failureId) it.copy(failure = null) else it }
    }

    /**
     * 画面が裏に回った（ON_STOP）。今の本文を画面の状態に残す（下書き 詳細設計 2 章）。
     * システムがアプリを終了しても、戻ってきたときにこの本文で ViewModel が作り直される。
     * 保存・削除・閉じる処理の途中や、読み込み中（本文がまだない）は何もしない。
     */
    fun onStop() {
        val state = _uiState.value
        if (state.isLoading || state.isBusy || state.isClosed) return
        savedStateHandle[KEY_DRAFT_BODY] = body
    }

    /**
     * DB に書く前に、書きかけを捨てて「終わった」印を付ける（下書き 詳細設計 6 章）。
     * Android が画面の状態を保存するのは裏に回ったとき（ON_STOP の後）の 1 回だけなので、書き込みの途中で裏に回り、
     * そのあとに書き込みが終わっても、保存された状態に印が入っているようにする。古い書きかけも残らない。
     */
    private fun startWriting() {
        savedStateHandle.remove<String>(KEY_DRAFT_BODY)
        savedStateHandle[KEY_CLOSED] = true
    }

    /** 保存・削除が終わった。印は書く前に付けてあるので、閉じるだけ */
    private fun finish(savedMemo: SavedMemo?) {
        close(savedMemo = savedMemo)
    }

    /**
     * 保存・削除が失敗した。落とさずに編集画面に残る（メモ編集 詳細設計 9.2 の 5）。
     * 終わった印を外し、捨てた書きかけを今の本文で戻し、処理中を解く。本文・memoId・original は触らない。ログに本文は出さない。
     */
    private fun fail(failure: EditFailure, body: String, e: Exception) {
        Log.w(TAG, "${if (failure == EditFailure.SaveFailed) "保存" else "削除"}に失敗: ${e::class.simpleName}")
        savedStateHandle.remove<Boolean>(KEY_CLOSED)
        savedStateHandle[KEY_DRAFT_BODY] = body
        // 通し番号を進める。同じ種類の失敗が続いても、画面は新しい失敗として出す
        _uiState.update { it.copy(isBusy = false, failure = failure, failureId = it.failureId + 1) }
    }

    /** 一覧へ戻る。保存したときだけ [savedMemo] を渡す（一覧をそのメモの行へ動かす合図。メモ一覧 詳細設計 9.3） */
    private fun close(savedMemo: SavedMemo? = null) {
        // 下書き（画面の状態）は画面を閉じると一緒に捨てられるので、消す処理は要らない（下書き 詳細設計 2 章）
        _uiState.update { it.copy(isClosed = true, savedMemo = savedMemo) }
    }

    companion object {
        const val KEY_MEMO_ID = "memoId"
        const val KEY_DRAFT_BODY = "draftBody"

        /** 保存・削除が終わったことの印（下書き 詳細設計 6 章） */
        const val KEY_CLOSED = "closed"

        private const val TAG = "MemoEditViewModel"
    }
}
