package com.example.sinmemo.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.ui.navigation.SinMemoNavHost
import com.example.sinmemo.ui.theme.FontSize
import com.example.sinmemo.ui.theme.LocalFontSize
import com.example.sinmemo.ui.theme.SinMemoTheme

/**
 * アプリの画面全体。テーマと文字サイズの設定をここで反映する（設定 詳細設計 5 章）。
 * [content] は UI テストで NavHost の代わりに好きな画面を入れるためのもの。
 */
@Composable
fun SinMemoApp(
    darkTheme: Boolean,
    fontSize: FontSizeSetting,
    content: @Composable () -> Unit = { SinMemoNavHost() },
) {
    SinMemoTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalFontSize provides FontSize.from(fontSize)) {
            // 画面の切り替え（フェード）の途中に、後ろのウィンドウの白い背景が透けて見えないよう、
            // テーマの背景色で塗っておく（ダークのときに白く光るのを防ぐ）
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}
