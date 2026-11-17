package com.example.sinmemo.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen

/**
 * 設定を Preferences DataStore に保存する（共通 詳細設計 6 章、設定 詳細設計 3 章・10 章）。
 * 値は enum の名前で保存し、値が無い・知らない値・型違いは項目ごとに初期値として扱う。
 */
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val resetFlag: SettingsResetFlag,
) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        // 記憶装置の一時的なエラー（IOException）は、少し待って読み直す（10.3）。初期値は流さない
        // （読めないときに初期値で開くのは MainViewModel の 3 秒の打ち切りに任せる）。
        // 壊れたファイルは corruptionHandler が先に初期値で上書きするので、ここには来ない
        .retryWhen { cause, attempt ->
            if (cause is IOException) {
                delay(retryDelayMillis(attempt))
                true
            } else {
                false
            }
        }
        .map { prefs ->
            val default = UserSettings()
            UserSettings(
                themeMode = prefs.enumOrNull(THEME_MODE) ?: default.themeMode,
                fontSize = prefs.enumOrNull(FONT_SIZE) ?: default.fontSize,
            )
        }
        .distinctUntilChanged()

    override val wasReset: StateFlow<Boolean> = resetFlag.wasReset

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    override suspend fun setFontSize(size: FontSizeSetting) {
        dataStore.edit { it[FONT_SIZE] = size.name }
    }

    override fun clearResetNotice() {
        resetFlag.clear()
    }

    companion object {
        /** DataStore のファイル名（拡張子 .preferences_pb は DataStore が付ける） */
        const val FILE_NAME = "settings"

        internal val THEME_MODE = stringPreferencesKey("theme_mode")
        internal val FONT_SIZE = stringPreferencesKey("font_size")

        /** 読み直すまでの待ち時間。0.5 秒、1 秒、1.5 秒…最大 5 秒（10.3） */
        internal fun retryDelayMillis(attempt: Long): Long = com.example.sinmemo.data.retryDelayMillis(attempt)
    }
}

/**
 * 設定の DataStore を作る（10.2）。壊れたファイル（CorruptionException）は空（= 初期値）で上書きして読み続け、
 * [flag] に印を付ける。アプリ（DataStoreModule）とテストで同じ作り方を使う。
 * [scope] が null なら DataStore の既定の scope を使う。
 */
internal fun createSettingsDataStore(
    produceFile: () -> File,
    scope: CoroutineScope? = null,
    flag: SettingsResetFlag,
): DataStore<Preferences> {
    val handler = ReplaceFileCorruptionHandler {
        flag.markReset()
        emptyPreferences()
    }
    return if (scope == null) {
        PreferenceDataStoreFactory.create(corruptionHandler = handler, produceFile = produceFile)
    } else {
        PreferenceDataStoreFactory.create(corruptionHandler = handler, scope = scope, produceFile = produceFile)
    }
}

/** 名前が [E] のどれかに一致すればそれを返す。値が無い・知らない名前・文字列でない（型違い）なら null。 */
private inline fun <reified E : Enum<E>> Preferences.enumOrNull(key: Preferences.Key<String>): E? {
    // 型違いの値が入っていると get が ClassCastException を投げるので、asMap から取り出して調べる
    val name = asMap()[key] as? String ?: return null
    return enumValues<E>().firstOrNull { it.name == name }
}
