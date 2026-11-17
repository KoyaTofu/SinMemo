package com.example.sinmemo.data.settings

import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel のテスト用の偽リポジトリ（設定 詳細設計 4 章・10.2）。メモリ上で動き、保存した回数を数える。
 * [failWrites] を true にすると、保存で IOException を投げる（値は変えない）。
 * [resetFlag] を true にすると、壊れたファイルを上書きしたことになる。
 */
class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {

    val state = MutableStateFlow(initial)
    var saveCount = 0
        private set
    var failWrites = false

    val resetFlag = MutableStateFlow(false)

    override val settings: StateFlow<UserSettings> = state

    override val wasReset: StateFlow<Boolean> = resetFlag

    override suspend fun setThemeMode(mode: ThemeMode) {
        saveCount++
        if (failWrites) throw IOException("書き込みに失敗（テスト）")
        state.value = state.value.copy(themeMode = mode)
    }

    override suspend fun setFontSize(size: FontSizeSetting) {
        saveCount++
        if (failWrites) throw IOException("書き込みに失敗（テスト）")
        state.value = state.value.copy(fontSize = size)
    }

    override fun clearResetNotice() {
        resetFlag.value = false
    }
}
