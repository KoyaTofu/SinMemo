package com.example.sinmemo.data.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 「壊れた設定のファイルを初期値で上書きした」印（設定 詳細設計 10.2）。 */
@Singleton
class SettingsResetFlag @Inject constructor() {
    private val _wasReset = MutableStateFlow(false)
    val wasReset: StateFlow<Boolean> = _wasReset.asStateFlow()

    fun markReset() {
        _wasReset.value = true
    }

    fun clear() {
        _wasReset.value = false
    }
}
