package com.example.sinmemo.data.memo

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** テーブル `memos` を読み書きする SQL（共通 詳細設計 4.2）。 */
@Dao
interface MemoDao {

    /** 一覧用。更新日時の新しい順、同じなら id の大きい順。本文は先頭 200 文字だけ読む */
    @Query(
        """
        SELECT id, substr(body, 1, 200) AS head, updated_at
        FROM memos
        ORDER BY updated_at DESC, id DESC
        """
    )
    fun observeSummaries(): Flow<List<MemoSummary>>

    @Query("SELECT * FROM memos WHERE id = :id")
    suspend fun getById(id: Long): Memo?

    /** 追加して、採番された id を返す */
    @Insert
    suspend fun insert(memo: Memo): Long

    /** 本文と更新日時を書き換え、変わった行数を返す */
    @Query("UPDATE memos SET body = :body, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateBody(id: Long, body: String, updatedAt: Long): Int

    /** 削除して、消えた行数を返す */
    @Query("DELETE FROM memos WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
