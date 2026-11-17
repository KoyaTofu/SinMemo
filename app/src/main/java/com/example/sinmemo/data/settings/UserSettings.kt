package com.example.sinmemo.data.settings

/** テーマ（F-17）。DataStore には名前（SYSTEM など）で保存する（設定 詳細設計 3 章）。 */
enum class ThemeMode {
    /** 端末の設定に従う */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    /** ダークの配色で描くか。[systemDark] は端末がダークモードか。 */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}

/**
 * 文字サイズ（F-18）の「小・中・大」。sp の値は画面の都合なので UI 層の FontSize に置き、
 * データ層はどれを選んだかだけを持つ（設定 詳細設計 2 章）。
 */
enum class FontSizeSetting { SMALL, MEDIUM, LARGE }

/** 保存している設定。初期値はテーマ「ライト」、文字サイズ「中」（画面一覧 決定事項 9）。 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val fontSize: FontSizeSetting = FontSizeSetting.MEDIUM,
)
