package com.example.sinmemo.data.memo

import androidx.room.ColumnInfo

/** 一覧の 1 行に使う値。本文は先頭の [HEAD_LENGTH] 文字だけを持つ（共通 詳細設計 4.2）。 */
data class MemoSummary(
    val id: Long,
    /** 本文の先頭 [HEAD_LENGTH] 文字 */
    val head: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
) {
    companion object {
        /** 一覧のために読む本文の長さ。MemoDao のクエリの値と合わせること */
        const val HEAD_LENGTH = 200
    }
}
