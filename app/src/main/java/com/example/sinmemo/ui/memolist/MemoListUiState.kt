package com.example.sinmemo.ui.memolist

/** S-01 の画面の状態（メモ一覧 詳細設計 3 章）。 */
data class MemoListUiState(
    val items: List<MemoListItem> = emptyList(),
)

/** 一覧の 1 行。 */
data class MemoListItem(
    val id: Long,
    /** 本文の、空白だけでない最初の行 */
    val title: String,
    /** 更新日時を表示用に整形したもの */
    val updatedAt: String,
    /** 整形する前の更新日時（エポックミリ秒）。保存したメモが新しい一覧に入ったかの判定に使う（メモ一覧 詳細設計 9.3） */
    val updatedAtMillis: Long = 0L,
)
