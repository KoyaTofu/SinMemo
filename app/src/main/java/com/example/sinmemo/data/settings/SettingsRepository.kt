package com.example.sinmemo.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 設定の出し入れ（設定 詳細設計 4 章・10.2）。 */
interface SettingsRepository {
    /** 今の設定。変わるたびに新しい値が流れる。読めるまでは何も流れない */
    val settings: Flow<UserSettings>

    /** 壊れた設定のファイルを初期値で上書きしたら true（メッセージを出したら [clearResetNotice] で戻す） */
    val wasReset: StateFlow<Boolean>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setFontSize(size: FontSizeSetting)

    /** [wasReset] を false に戻す */
    fun clearResetNotice()
}
