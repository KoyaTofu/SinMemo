package com.example.sinmemo.ui.memolist

import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** 一覧の表示用の整形（メモ一覧 基本設計 3 章・詳細設計 5 章）。Android に依存しないので PC で単体テストできる。 */
object MemoListFormatter {

    private val thisYearFormat = DateTimeFormatter.ofPattern("MM/dd HH:mm")
    private val otherYearFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

    /**
     * 本文の先頭から、空白だけでない最初の行を返す（行の中身は加工しない）。
     * すべての行が空白だけなら空文字。
     */
    fun title(head: String): String =
        head.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

    /**
     * 更新日時を文字列にする。[now] と同じ年なら `MM/dd HH:mm`、それ以外の年なら `yyyy/MM/dd HH:mm`。
     * タイムゾーンは [now] のものを使う。
     */
    fun updatedAt(updatedAtMillis: Long, now: ZonedDateTime): String {
        val dateTime = Instant.ofEpochMilli(updatedAtMillis).atZone(now.zone)
        val format = if (dateTime.year == now.year) thisYearFormat else otherYearFormat
        return dateTime.format(format)
    }
}
