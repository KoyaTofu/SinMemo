package com.example.sinmemo.ui.settings

import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.data.settings.ThemeMode
import com.example.sinmemo.data.settings.UserSettings

/** S-03 の状態（設定 詳細設計 7 章）。 */
data class SettingsUiState(
    /** 保存している設定（項目の 2 行目に出す今の値） */
    val settings: UserSettings = UserSettings(),
    /** 開いているダイアログ。null なら閉じている */
    val dialog: SettingsDialog? = null,
)

/** 開いているダイアログと、その中で印が付いている選択肢（まだ保存していない） */
sealed interface SettingsDialog {
    /** D-03 テーマ */
    data class Theme(val selected: ThemeMode) : SettingsDialog

    /** D-04 文字サイズ */
    data class FontSize(val selected: FontSizeSetting) : SettingsDialog
}
