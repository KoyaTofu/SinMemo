package com.example.sinmemo.data.settings

import com.example.sinmemo.ui.theme.FontSize
import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.unit.sp

/** ThemeMode.isDark と FontSize.from（設定 テスト設計 1 章）。 */
class UserSettingsTest {

    @Test
    fun defaults_areLightAndMedium() {
        assertEquals(ThemeMode.LIGHT, UserSettings().themeMode)
        assertEquals(FontSizeSetting.MEDIUM, UserSettings().fontSize)
    }

    @Test
    fun isDark_allCombinations() {
        val expected = mapOf(
            (ThemeMode.SYSTEM to false) to false,
            (ThemeMode.SYSTEM to true) to true,
            (ThemeMode.LIGHT to false) to false,
            (ThemeMode.LIGHT to true) to false,
            (ThemeMode.DARK to false) to true,
            (ThemeMode.DARK to true) to true,
        )
        expected.forEach { (input, dark) ->
            assertEquals("${input.first} / 端末ダーク=${input.second}", dark, input.first.isDark(systemDark = input.second))
        }
    }

    @Test
    fun fontSizeFrom_mapsEachSetting() {
        assertEquals(FontSize.SMALL, FontSize.from(FontSizeSetting.SMALL))
        assertEquals(FontSize.MEDIUM, FontSize.from(FontSizeSetting.MEDIUM))
        assertEquals(FontSize.LARGE, FontSize.from(FontSizeSetting.LARGE))
        // 値は共通 基本設計 4 章の表どおり
        assertEquals(listOf(15.sp, 12.sp, 15.sp), FontSize.SMALL.let { listOf(it.title, it.date, it.body) })
        assertEquals(listOf(17.sp, 13.sp, 17.sp), FontSize.MEDIUM.let { listOf(it.title, it.date, it.body) })
        assertEquals(listOf(20.sp, 15.sp, 20.sp), FontSize.LARGE.let { listOf(it.title, it.date, it.body) })
    }
}
