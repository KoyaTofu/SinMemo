package com.example.sinmemo.ui.memolist

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** MemoListFormatter の単体テスト（メモ一覧 基本設計 3 章・テスト設計 2.1）。Android に依存しないので素の JUnit で動かす。 */
class MemoListFormatterTest {

    // ---- title ----

    @Test
    fun title_returnsFirstLine() {
        assertEquals("牛乳、卵", MemoListFormatter.title("牛乳、卵\nパン"))
    }

    @Test
    fun title_returnsWholeText_whenNoNewline() {
        assertEquals("1 行だけ", MemoListFormatter.title("1 行だけ"))
    }

    @Test
    fun title_skipsEmptyAndBlankLines() {
        assertEquals("打ち合わせメモ", MemoListFormatter.title("\n\n   \n\t\n打ち合わせメモ\n議題"))
    }

    @Test
    fun title_skipsLinesOfFullWidthSpaces() {
        assertEquals("本文", MemoListFormatter.title("　　\n本文"))
    }

    @Test
    fun title_keepsLeadingSpacesOfTheLine() {
        // 行の中身は加工しない（メモ一覧 基本設計 3.1）
        assertEquals("  字下げ", MemoListFormatter.title("\n  字下げ"))
    }

    @Test
    fun title_handlesCrLf() {
        assertEquals("Windows の改行", MemoListFormatter.title("\r\nWindows の改行\r\n2 行目"))
    }

    @Test
    fun title_returnsEmpty_whenAllLinesAreBlank() {
        assertEquals("", MemoListFormatter.title(" \n　\n\t"))
    }

    @Test
    fun title_returnsEmpty_whenEmpty() {
        assertEquals("", MemoListFormatter.title(""))
    }

    // ---- updatedAt ----

    private val utc = ZoneOffset.UTC
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun millis(text: String, zone: ZoneId): Long =
        java.time.LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun now(text: String, zone: ZoneId): ZonedDateTime =
        java.time.LocalDateTime.parse(text).atZone(zone)

    @Test
    fun updatedAt_omitsYear_whenThisYear() {
        assertEquals(
            "11/12 14:30",
            MemoListFormatter.updatedAt(millis("2026-11-12T14:30", utc), now("2026-11-12T15:00", utc)),
        )
    }

    @Test
    fun updatedAt_padsWithZeros() {
        assertEquals(
            "01/05 09:03",
            MemoListFormatter.updatedAt(millis("2026-01-05T09:03", utc), now("2026-11-12T15:00", utc)),
        )
    }

    @Test
    fun updatedAt_showsYear_whenLastYear() {
        assertEquals(
            "2025/12/28 21:04",
            MemoListFormatter.updatedAt(millis("2025-12-28T21:04", utc), now("2026-11-12T15:00", utc)),
        )
    }

    @Test
    fun updatedAt_showsYear_whenFutureYear() {
        // 端末の時計が狂っていた場合（メモ一覧 基本設計 3.2）
        assertEquals(
            "2027/02/03 04:05",
            MemoListFormatter.updatedAt(millis("2027-02-03T04:05", utc), now("2026-11-12T15:00", utc)),
        )
    }

    @Test
    fun updatedAt_usesTwentyFourHourClock() {
        assertEquals(
            "11/12 23:59",
            MemoListFormatter.updatedAt(millis("2026-11-12T23:59", utc), now("2026-11-13T00:00", utc)),
        )
    }

    @Test
    fun updatedAt_atNewYear_lastMinuteOfLastYearShowsYear() {
        val now = now("2027-01-01T00:00", utc)
        assertEquals("2026/12/31 23:59", MemoListFormatter.updatedAt(millis("2026-12-31T23:59", utc), now))
        assertEquals("01/01 00:00", MemoListFormatter.updatedAt(millis("2027-01-01T00:00", utc), now))
    }

    @Test
    fun updatedAt_usesTimeZoneOfNow() {
        // UTC の 2025-12-31 15:30 は、日本では 2026-01-01 00:30
        val instant = millis("2025-12-31T15:30", utc)
        assertEquals("01/01 00:30", MemoListFormatter.updatedAt(instant, now("2026-01-10T12:00", tokyo)))
        assertEquals("2025/12/31 15:30", MemoListFormatter.updatedAt(instant, now("2026-01-10T12:00", utc)))
    }

    // ---- 7.4（#59）割り切りと調べの確認（メモ一覧 テスト設計 2.7） ----

    /** L-U-40 先頭 200 文字が空白なら、その後ろに文字があっても head（200 文字）からは空（割り切りどおり） */
    @Test
    fun title_isEmpty_whenFirst200CharsAreBlank() {
        val body = " ".repeat(100) + "\n" + " ".repeat(99) + "本文"
        val head = body.take(200)
        assertEquals("", MemoListFormatter.title(head))
    }

    /** L-U-41 ゼロ幅の文字（U+200B）だけの行は空白と見なさず、そのまま返す */
    @Test
    fun title_keepsZeroWidthOnlyLine() {
        assertEquals("\u200B", MemoListFormatter.title("\u200B\n2 行目"))
    }

    /** L-U-42 夏時間のあるタイムゾーンで、夏と冬の同じ世界時が 1 時間違って出る */
    @Test
    fun updatedAt_handlesDaylightSaving() {
        val london = ZoneId.of("Europe/London")
        val now = now("2026-11-12T15:00", london)
        assertEquals("07/01 13:00", MemoListFormatter.updatedAt(millis("2026-07-01T12:00", utc), now))
        assertEquals("01/15 12:00", MemoListFormatter.updatedAt(millis("2026-01-15T12:00", utc), now))
    }

    /** L-U-43 絵文字（サロゲートペア）を含む 1 行目がそのまま返る */
    @Test
    fun title_withEmoji_isNotCut() {
        assertEquals("😀 買い物 👍", MemoListFormatter.title("😀 買い物 👍\n2 行目"))
    }
}
