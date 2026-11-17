package com.example.sinmemo

/**
 * スプラッシュを出し続けるかの判定（設定 基本設計 5 章・決定事項 3、詳細設計 6 章）。Android に依存しないので単体テストできる。
 *
 * - 設定を読み終わるまでは出し続ける（初期値で描いてから切り替わる「ちらつき」を防ぐ）
 * - 新しく起動したとき（[applyMinimum] = true）は、読み終わっても [startMillis] から
 *   [MIN_DURATION_MILLIS] たつまで出し続ける（一瞬だけ見えて消える感じを出さない）
 * - 画面の回転や、システムに終了されたあと画面ごと戻るときは最小時間をかけない
 */
class SplashGate(
    private val startMillis: Long,
    private val applyMinimum: Boolean,
) {
    fun shouldKeepOnScreen(nowMillis: Long, settingsLoaded: Boolean): Boolean {
        if (!settingsLoaded) return true
        return applyMinimum && nowMillis - startMillis < MIN_DURATION_MILLIS
    }

    companion object {
        /** 新しく起動したときの最小表示時間 */
        const val MIN_DURATION_MILLIS = 500L

        /** スプラッシュが消えるときのフェードアウトの時間 */
        const val FADE_OUT_MILLIS = 200L
    }
}
