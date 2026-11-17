package com.example.sinmemo.data.memo

import kotlinx.coroutines.flow.Flow

/**
 * メモの出し入れ（共通 詳細設計 4.3・13.1）。
 * 画面（ViewModel）はこの interface だけを使い、テストでは偽物に差し替える。
 * DB の例外は握りつぶさずに呼び出し元へ投げる（受け止めるのは ViewModel。13.2）。
 */
interface MemoRepository {
    /** 一覧。保存・削除のたびに新しい一覧が流れてくる */
    fun observeSummaries(): Flow<List<MemoSummary>>

    /** 1 件取得。なければ null */
    suspend fun getMemo(id: Long): Memo?

    /** 新規保存。作成日時・更新日時に現在時刻を入れ、採番された id と書いた更新日時を返す */
    suspend fun create(body: String): SavedMemo

    /**
     * 既存の本文を書き換え、更新日時を現在時刻にする。書いた id と更新日時を返す。
     * メモがない（書き換えた行が 0 行）なら null。
     */
    suspend fun update(id: Long, body: String): SavedMemo?

    /** 削除。メモがなくても（0 行でも）例外にしない */
    suspend fun delete(id: Long)
}
