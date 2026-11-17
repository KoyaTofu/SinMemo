package com.example.sinmemo.data.memo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 新規（まだ保存していない）メモを表す id。Room の自動採番は 1 から始まるので 0 は使われない。 */
const val NEW_MEMO_ID = 0L

/** メモ 1 件。テーブル `memos` の 1 行（共通 詳細設計 4.1）。 */
@Entity(
    tableName = "memos",
    indices = [Index(value = ["updated_at"])],
)
data class Memo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = NEW_MEMO_ID,
    val body: String,
    /** 作成日時（エポックミリ秒） */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    /** 更新日時（エポックミリ秒）。一覧の並び順に使う */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
