package com.example.sinmemo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * アプリのテーマ。モックアップの配色を使い、ダイナミックカラーは使わない（共通 基本設計 3 章・詳細設計 8.1）。
 * [darkTheme] はテーマ設定（端末に従う／ライト／ダーク）から MainActivity が決める（設定 基本設計 4 章・詳細設計 5 章）。
 * 省略したときはライト（設定の初期値。プレビュー用）。
 */
@Composable
fun SinMemoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
