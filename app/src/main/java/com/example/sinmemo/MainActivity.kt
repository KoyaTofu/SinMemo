package com.example.sinmemo

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sinmemo.ui.SinMemoApp
import dagger.hilt.android.AndroidEntryPoint

/** 唯一の Activity。画面の切り替えは Navigation Compose で行う（画面一覧 実装方針）。 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // スプラッシュ（設定 基本設計 5 章・詳細設計 6 章）。super.onCreate より前に呼ぶ決まり
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // 最小表示時間は新しく起動したときだけ（回転・画面ごとの復元では待たない）
        val gate = SplashGate(
            startMillis = SystemClock.elapsedRealtime(),
            applyMinimum = savedInstanceState == null,
        )
        // 画面を描こうとするたびに呼ばれ、true の間はスプラッシュのまま
        splashScreen.setKeepOnScreenCondition {
            gate.shouldKeepOnScreen(SystemClock.elapsedRealtime(), viewModel.settings.value != null)
        }
        splashScreen.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .setDuration(SplashGate.FADE_OUT_MILLIS)
                .withEndAction { provider.remove() }
                .start()
        }

        setSystemBarStyle(darkTheme = false)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            // 設定を読み終わるまでは何も描かない（スプラッシュが出ている）
            val current = settings ?: return@setContent
            // 設定の読み込みについてのメッセージを、起きた順に 1 つずつトーストで出す（設定 詳細設計 10.5）
            val notices by viewModel.notices.collectAsStateWithLifecycle()
            SettingsNoticeToasts(notices = notices, onShown = viewModel::onNoticeShown)
            val darkTheme = current.themeMode.isDark(systemDark = isSystemInDarkTheme())
            // ステータスバー・ナビゲーションバーのアイコンの色をテーマに合わせる（設定 基本設計 4 章・詳細設計 5 章）
            DisposableEffect(darkTheme) {
                setSystemBarStyle(darkTheme)
                onDispose {}
            }
            SinMemoApp(darkTheme = darkTheme, fontSize = current.fontSize)
        }
    }

    /** ライトなら暗いアイコン、ダークなら明るいアイコン。背景は透明（edge-to-edge）。 */
    private fun setSystemBarStyle(darkTheme: Boolean) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

/**
 * 順番待ちの先頭のメッセージをトーストで出し、[onShown] にその通し番号を渡す（設定 詳細設計 10.5）。
 * 通し番号を鍵にするので、同じ種類のメッセージが続いても（[Reset, Reset]）2 つ目も出る。
 */
@Composable
internal fun SettingsNoticeToasts(notices: List<QueuedNotice>, onShown: (Long) -> Unit) {
    val context = LocalContext.current
    // 文は画面の設定（言語など）に合わせて取り出しておく
    val loadTimeout = stringResource(R.string.settings_load_timeout)
    val reset = stringResource(R.string.settings_reset)
    val first = notices.firstOrNull()
    LaunchedEffect(first?.id) {
        if (first != null) {
            val message = when (first.notice) {
                SettingsNotice.LoadTimeout -> loadTimeout
                SettingsNotice.Reset -> reset
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onShown(first.id)
        }
    }
}
