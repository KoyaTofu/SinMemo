package com.example.sinmemo.data.memo

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * ViewModel のテスト用の偽リポジトリ（共通 詳細設計 4.3・13.1）。DB を使わず、メモリ上のリストで動く。
 * 日時は呼び出し側が [nowMillis] で決める。
 *
 * 異常系を起こす口:
 * - [failNextWith]: 次の create・update・delete で 1 回だけ投げる
 * - [removeBeforeUpdate]: 次の update の前にそのメモを消す（0 行の更新を起こす）
 * - [gate]: create・update・delete の中で、開く（complete）まで待つ（処理中の状態を作る）
 */
class FakeMemoRepository(
    var nowMillis: Long = 0L,
) : MemoRepository {

    private val memos = MutableStateFlow<List<Memo>>(emptyList())
    private var nextId = 1L

    var failNextWith: Throwable? = null
    var removeBeforeUpdate: Boolean = false
    var gate: CompletableDeferred<Unit>? = null

    var getMemoCalls = 0
        private set
    var createCalls = 0
        private set
    var updateCalls = 0
        private set
    var deleteCalls = 0
        private set

    /** テストの準備用。メモをそのまま入れる（id が 0 なら採番する） */
    fun put(vararg items: Memo) {
        memos.value = memos.value + items.map { if (it.id == NEW_MEMO_ID) it.copy(id = nextId++) else it }
        nextId = maxOf(nextId, (memos.value.maxOfOrNull { it.id } ?: 0L) + 1)
    }

    /** テストの準備用。メモをそのまま消す（ほかで消えた状態を作る） */
    fun remove(id: Long) {
        memos.value = memos.value.filterNot { it.id == id }
    }

    override fun observeSummaries(): Flow<List<MemoSummary>> = memos.map { list ->
        list.sortedWith(compareByDescending<Memo> { it.updatedAt }.thenByDescending { it.id })
            .map { MemoSummary(id = it.id, head = it.body.take(MemoSummary.HEAD_LENGTH), updatedAt = it.updatedAt) }
    }

    override suspend fun getMemo(id: Long): Memo? {
        getMemoCalls++
        return memos.value.firstOrNull { it.id == id }
    }

    override suspend fun create(body: String): SavedMemo {
        createCalls++
        beforeWrite()
        val id = nextId++
        memos.value = memos.value + Memo(id = id, body = body, createdAt = nowMillis, updatedAt = nowMillis)
        return SavedMemo(id = id, updatedAt = nowMillis)
    }

    override suspend fun update(id: Long, body: String): SavedMemo? {
        updateCalls++
        beforeWrite()
        if (removeBeforeUpdate) {
            removeBeforeUpdate = false
            remove(id)
        }
        if (memos.value.none { it.id == id }) return null
        memos.value = memos.value.map { if (it.id == id) it.copy(body = body, updatedAt = nowMillis) else it }
        return SavedMemo(id = id, updatedAt = nowMillis)
    }

    override suspend fun delete(id: Long) {
        deleteCalls++
        beforeWrite()
        memos.value = memos.value.filterNot { it.id == id }
    }

    private suspend fun beforeWrite() {
        gate?.await()
        failNextWith?.let {
            failNextWith = null
            throw it
        }
    }
}
