package com.example.sinmemo.ui.memoedit

import com.example.sinmemo.data.memo.SavedMemo

/**
 * S-02 の画面の状態（メモ編集 詳細設計 2.1・9.1）。
 * 本文は含めず、ViewModel の TextFieldState を画面に渡す。本文から決まる「✓ を押せるか」「文字数の表示」は、
 * 画面側で本文から求める（[MemoTextLimit]）。
 */
data class MemoEditUiState(
    /** 既存メモの読み込み中（入力欄を出さない） */
    val isLoading: Boolean = false,
    /** 新規作成中（⋮ を出さない、キーボードを出す） */
    val isNew: Boolean = true,
    /** システムに終了されたあと、書きかけを戻して開いた（キーボードを自動で出さない。下書き 基本設計 3.2） */
    val isRestored: Boolean = false,
    /** 保存・削除の処理中（✓ と「削除」を受け付けない）。失敗したら false に戻る */
    val isBusy: Boolean = false,
    /** 表示中のダイアログ */
    val dialog: EditDialog? = null,
    /** 「10,000文字を超えるため入力できません」を出す */
    val showTooLongMessage: Boolean = false,
    /** 出す失敗のメッセージ。null なら出さない。出し終えたら onFailureShown で null に戻す */
    val failure: EditFailure? = null,
    /** 失敗の通し番号。失敗のたびに 1 増える。画面はこれを鍵にして、同じ種類の失敗が続いても新しく出す */
    val failureId: Int = 0,
    /** true になったら画面が一覧へ戻る */
    val isClosed: Boolean = false,
    /**
     * 新規・更新で保存して閉じたときの、保存したメモの id と DB に書いた更新日時。
     * 保存しないで閉じたとき（変更なしの ✓・←・破棄・削除）は null。
     * 一覧はこれを受け取ったときだけ、そのメモの行へ動かす（メモ一覧 詳細設計 9.3）
     */
    val savedMemo: SavedMemo? = null,
)

enum class EditDialog {
    /** D-01 削除確認 */
    Delete,

    /** D-02 破棄確認 */
    Discard,
}

/** 保存・削除の失敗と、保存しなかった理由（メモ編集 詳細設計 9.1） */
enum class EditFailure {
    /** 「保存できませんでした」 */
    SaveFailed,

    /** 「削除できませんでした」 */
    DeleteFailed,

    /** 「10,000文字を超えるため保存できません」（上限を超えた本文で ✓。保存しない） */
    OverLimit,
}
