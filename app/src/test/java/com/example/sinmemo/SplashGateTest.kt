package com.example.sinmemo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** スプラッシュを出し続けるかの判定（設定 基本設計 5 章・決定事項 3）。 */
class SplashGateTest {

    private val start = 10_000L

    @Test
    fun constants_matchDesign() {
        assertEquals(500L, SplashGate.MIN_DURATION_MILLIS)
        assertEquals(200L, SplashGate.FADE_OUT_MILLIS)
    }

    @Test
    fun newLaunch_keeps_untilSettingsLoaded_evenAfterMinimum() {
        val gate = SplashGate(start, applyMinimum = true)

        assertTrue(gate.shouldKeepOnScreen(start, settingsLoaded = false))
        assertTrue(gate.shouldKeepOnScreen(start + 5_000, settingsLoaded = false))
    }

    @Test
    fun newLaunch_keeps_untilMinimum_evenIfLoaded() {
        val gate = SplashGate(start, applyMinimum = true)

        assertTrue(gate.shouldKeepOnScreen(start, settingsLoaded = true))
        assertTrue(gate.shouldKeepOnScreen(start + 499, settingsLoaded = true))
    }

    @Test
    fun newLaunch_releases_atMinimum_whenLoaded() {
        val gate = SplashGate(start, applyMinimum = true)

        assertFalse(gate.shouldKeepOnScreen(start + 500, settingsLoaded = true))
        assertFalse(gate.shouldKeepOnScreen(start + 1_500, settingsLoaded = true))
    }

    @Test
    fun recreated_releases_assoonAsLoaded() {
        // 回転・システムに終了されたあと画面ごと戻るとき（savedInstanceState あり）
        val gate = SplashGate(start, applyMinimum = false)

        assertTrue(gate.shouldKeepOnScreen(start, settingsLoaded = false))
        assertFalse(gate.shouldKeepOnScreen(start, settingsLoaded = true))
    }
}
