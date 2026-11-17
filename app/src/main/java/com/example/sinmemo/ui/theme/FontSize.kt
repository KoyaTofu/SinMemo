package com.example.sinmemo.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.sinmemo.data.settings.FontSizeSetting

/**
 * 一覧・編集画面の文字サイズ（共通 基本設計 4 章・詳細設計 8.2）。値はモックアップの px をそのまま sp にしたもの。
 * 設定の値（[FontSizeSetting]）からは [from] で変換する（設定 詳細設計 2 章）。
 */
enum class FontSize(
    /** 一覧の 1 行目 */
    val title: TextUnit,
    /** 一覧の更新日時 */
    val date: TextUnit,
    /** 編集画面の本文 */
    val body: TextUnit,
) {
    SMALL(title = 15.sp, date = 12.sp, body = 15.sp),
    MEDIUM(title = 17.sp, date = 13.sp, body = 17.sp),
    LARGE(title = 20.sp, date = 15.sp, body = 20.sp),
    ;

    companion object {
        /** 初期値「中」（画面一覧 決定事項 9） */
        val DEFAULT = MEDIUM

        fun from(setting: FontSizeSetting): FontSize = when (setting) {
            FontSizeSetting.SMALL -> SMALL
            FontSizeSetting.MEDIUM -> MEDIUM
            FontSizeSetting.LARGE -> LARGE
        }
    }
}

/**
 * 今の文字サイズ。SinMemoApp が設定から決めて渡し、一覧・編集の画面が読む（設定 詳細設計 5 章）。
 * 変わるのは設定を変えたときだけなので、static（変わったら中身全体を描き直す）にしている。
 */
val LocalFontSize = staticCompositionLocalOf { FontSize.DEFAULT }
