package com.example.sinmemo.ui.memoedit

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import java.util.Locale

/** 本文の上限と文字数の表示（メモ編集 基本設計 3.4、作り方は詳細設計 5 章）。 */
object MemoTextLimit {
    /** 本文の上限（機能一覧 F-03） */
    const val MAX_LENGTH = 10_000

    /** この文字数以上で「9,688/10,000文字」を表示する（残り 500 文字） */
    const val SHOW_COUNT_FROM = 9_500

    /** 表示する文字数。表示しないときは null */
    fun countToShow(length: Int): Int? = if (length >= SHOW_COUNT_FROM) length else null

    /** 3 桁ごとにカンマで区切る（例: 9688 → "9,688"） */
    fun format(count: Int): String = String.format(Locale.US, "%,d", count)

    /**
     * 本文が上限を超えているか（保存できない）。数え方は入力の上限（[MaxLengthTransformation]）と同じ
     * （String の長さ = UTF-16 の数。絵文字 1 つは 2 以上）
     */
    fun isOverLimit(text: CharSequence): Boolean = text.length > MAX_LENGTH

    /** 本文が「空」か（空白・改行・全角スペースだけ、または何もない。モックアップ検証 論点 1） */
    fun isBlank(text: CharSequence): Boolean = text.isBlank()
}

/**
 * 変更後の文字数が [maxLength] を超え、かつ変更前より増える入力を、丸ごと取り消す（メモ編集 基本設計 3.4・決定事項 3、
 * メモ編集 詳細設計 9.6）。すでに上限を超えている本文（DB や書きかけから入ったもの）でも、減らす・同じ長さで
 * 置き換える変更は通す。
 * 取り消した入力で 2 文字以上増えるはずだった（貼り付けなど）なら [onRejected] を呼ぶ。
 * 1 文字ずつの入力では呼ばない。
 *
 * 「何文字入力されたか」を正確に知る API（TextFieldBuffer.changes）は試験的なため使わず、
 * 「変更前後の文字数の差」で判断する。選択範囲を置き換える貼り付けは、差し引きの増え方で判断される。
 */
class MaxLengthTransformation(
    private val maxLength: Int = MemoTextLimit.MAX_LENGTH,
    private val onRejected: () -> Unit,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        if (length <= maxLength || length <= originalText.length) return
        val grown = length - originalText.length
        revertAllChanges()
        if (grown >= 2) onRejected()
    }
}
