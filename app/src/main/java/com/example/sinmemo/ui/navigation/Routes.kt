package com.example.sinmemo.ui.navigation

import com.example.sinmemo.data.memo.NEW_MEMO_ID
import kotlinx.serialization.Serializable

// 画面遷移の行き先（共通 詳細設計 5 章）。ダイアログは行き先にせず、各画面の中で出す。

/** S-01 メモ一覧（開始画面） */
@Serializable
data object MemoListRoute

/**
 * S-02 メモ編集。[memoId] が [NEW_MEMO_ID]（0）なら新規。
 * MemoEditViewModel はこの引数を SavedStateHandle から名前 "memoId" で読むので、名前を変えるときは合わせて直す。
 */
@Serializable
data class MemoEditRoute(val memoId: Long = NEW_MEMO_ID)

/** S-03 設定 */
@Serializable
data object SettingsRoute
