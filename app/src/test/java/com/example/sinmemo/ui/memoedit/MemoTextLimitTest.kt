package com.example.sinmemo.ui.memoedit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** MemoTextLimit の単体テスト（メモ編集 基本設計 3.1・3.4）。 */
class MemoTextLimitTest {

    @Test
    fun countToShow_isNull_below9500() {
        assertNull(MemoTextLimit.countToShow(0))
        assertNull(MemoTextLimit.countToShow(9_499))
    }

    @Test
    fun countToShow_returnsCount_from9500() {
        assertEquals(9_500, MemoTextLimit.countToShow(9_500))
        assertEquals(10_000, MemoTextLimit.countToShow(10_000))
    }

    @Test
    fun format_separatesThousandsWithComma() {
        assertEquals("9,688", MemoTextLimit.format(9_688))
        assertEquals("10,000", MemoTextLimit.format(10_000))
        assertEquals("0", MemoTextLimit.format(0))
    }

    @Test
    fun isBlank_trueForEmptyAndWhitespaceOnly() {
        assertTrue(MemoTextLimit.isBlank(""))
        assertTrue(MemoTextLimit.isBlank("  \n\t"))
        assertTrue(MemoTextLimit.isBlank("　　")) // 全角スペース
    }

    @Test
    fun isBlank_falseWhenAnyVisibleChar() {
        assertFalse(MemoTextLimit.isBlank(" a "))
        assertFalse(MemoTextLimit.isBlank("\n本文"))
    }
}
