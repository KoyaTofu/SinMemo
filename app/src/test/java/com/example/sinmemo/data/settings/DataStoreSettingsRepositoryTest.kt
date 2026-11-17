package com.example.sinmemo.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * DataStoreSettingsRepository の単体テスト（設定 テスト設計 1 章）。PC 上の一時フォルダのファイルに保存する。
 * 「再起動」は、DataStore を閉じて（スコープを止めて）同じファイルで作り直すことで再現する。
 * DataStore は本物のファイル入出力をするので、仮想時間の runTest ではなく runBlocking を使う。
 */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file: File by lazy { File(tmp.root, "settings.preferences_pb") }
    private var job = Job()
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var flag: SettingsResetFlag

    /**
     * DataStore を作る（アプリの起動に当たる）。アプリと同じ createSettingsDataStore で作る（設定 詳細設計 10.2）。
     * 同じファイルに 2 つ同時に作れないので、前のものは止めておく
     */
    private fun open(wrap: (DataStore<Preferences>) -> DataStore<Preferences> = { it }): DataStoreSettingsRepository {
        job = Job()
        flag = SettingsResetFlag()
        dataStore = createSettingsDataStore(
            produceFile = { file },
            scope = CoroutineScope(Dispatchers.IO + job),
            flag = flag,
        )
        return DataStoreSettingsRepository(wrap(dataStore), flag)
    }

    /** アプリの終了に当たる */
    private suspend fun close() = job.cancelAndJoin()

    @After
    fun tearDown() = runBlocking { close() }

    @Test
    fun noFile_returnsDefaults() = runBlocking {
        val repository = open()

        assertEquals(UserSettings(ThemeMode.LIGHT, FontSizeSetting.MEDIUM), repository.settings.first())
    }

    @Test
    fun savedValues_areRead() = runBlocking {
        val repository = open()

        repository.setThemeMode(ThemeMode.DARK)
        repository.setFontSize(FontSizeSetting.LARGE)

        assertEquals(UserSettings(ThemeMode.DARK, FontSizeSetting.LARGE), repository.settings.first())
    }

    @Test
    fun allValues_canBeSavedAndRead() = runBlocking {
        val repository = open()
        for (mode in ThemeMode.entries) {
            repository.setThemeMode(mode)
            assertEquals(mode, repository.settings.first().themeMode)
        }
        for (size in FontSizeSetting.entries) {
            repository.setFontSize(size)
            assertEquals(size, repository.settings.first().fontSize)
        }
    }

    @Test
    fun values_surviveRestart() = runBlocking {
        open().run {
            setThemeMode(ThemeMode.SYSTEM)
            setFontSize(FontSizeSetting.SMALL)
        }
        close()

        val reopened = open()

        assertEquals(UserSettings(ThemeMode.SYSTEM, FontSizeSetting.SMALL), reopened.settings.first())
    }

    @Test
    fun valuesAreStoredAsEnumNames_withDesignedKeys() = runBlocking {
        val repository = open()
        repository.setThemeMode(ThemeMode.DARK)
        repository.setFontSize(FontSizeSetting.SMALL)

        val raw = dataStore.data.first().asMap().mapKeys { it.key.name }

        assertEquals(mapOf("theme_mode" to "DARK", "font_size" to "SMALL"), raw)
    }

    @Test
    fun onlyOneSaved_otherIsDefault() = runBlocking {
        val repository = open()

        repository.setFontSize(FontSizeSetting.LARGE)

        assertEquals(UserSettings(ThemeMode.LIGHT, FontSizeSetting.LARGE), repository.settings.first())
    }

    @Test
    fun unknownValue_isDefault_perItem() = runBlocking {
        val repository = open()
        repository.setFontSize(FontSizeSetting.LARGE)
        // 将来の版で増やした値を古い版で読んだ場合など
        dataStore.edit { it[DataStoreSettingsRepository.THEME_MODE] = "SEPIA" }

        // テーマだけ初期値、文字サイズは生きている
        assertEquals(UserSettings(ThemeMode.LIGHT, FontSizeSetting.LARGE), repository.settings.first())
    }

    @Test
    fun wrongType_isDefault() = runBlocking {
        val repository = open()
        repository.setThemeMode(ThemeMode.DARK)
        // 同じ名前のキーに文字列でなく数値が入っている
        dataStore.edit { it[intPreferencesKey("font_size")] = 2 }

        assertEquals(UserSettings(ThemeMode.DARK, FontSizeSetting.MEDIUM), repository.settings.first())
    }

    @Test
    fun corruptedFile_returnsDefaults_withoutCrash() = runBlocking {
        file.writeBytes(byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x04))

        val repository = open()

        assertEquals(UserSettings(), repository.settings.first())
    }

    @Test
    fun settingsFlow_emitsWhenChanged() = runBlocking {
        val repository = open()
        val received = mutableListOf<UserSettings>()
        val collector = launch { repository.settings.collect { received += it } }
        // 最初の値（初期値）が届くまで待ってから変える
        withTimeout(5_000) { while (received.isEmpty()) delay(10) }

        repository.setThemeMode(ThemeMode.DARK)

        withTimeout(5_000) { while (received.size < 2) delay(10) }
        collector.cancel()
        assertEquals(listOf(UserSettings(), UserSettings(themeMode = ThemeMode.DARK)), received)
    }

    // ---- 7.4（#59）壊れたファイル・読み直し（設定 テスト設計 1.3） ----

    private val corrupted = byteArrayOf(0x7f, 0x01, 0x02, 0x03, 0x04)

    /** ST-U-49 */
    @Test
    fun corruptedFile_isOverwritten_andFlagIsSet() = runBlocking {
        file.writeBytes(corrupted)

        val repository = open()

        assertEquals(UserSettings(), repository.settings.first())
        assertTrue(repository.wasReset.value)
        // ファイルは空の設定で上書きされている
        assertFalse(file.readBytes().contentEquals(corrupted))
        assertEquals(emptyMap<Preferences.Key<*>, Any>(), dataStore.data.first().asMap())
        close()

        // 次の起動では壊れていないので、印は付かない
        val reopened = open()
        assertEquals(UserSettings(), reopened.settings.first())
        assertFalse(reopened.wasReset.value)
    }

    /** ST-U-50 壊れたファイルのまま「OK」で落ちていた不具合 */
    @Test
    fun corruptedFile_thenSave_works() = runBlocking {
        file.writeBytes(corrupted)
        val repository = open()

        repository.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, repository.settings.first().themeMode)
    }

    /** ST-U-51 一度読めなくても読み直し、そのあとの変更も流れてくる */
    @Test
    fun ioErrorOnce_thenRecovers_andFollowsLaterChanges() = runBlocking {
        open().setThemeMode(ThemeMode.DARK)
        close()
        lateinit var flaky: FlakyDataStore
        val repository = open { inner -> FlakyDataStore(inner, IOException("一時的なエラー（テスト）")).also { flaky = it } }
        val received = mutableListOf<UserSettings>()
        val collector = launch { repository.settings.collect { received += it } }

        withTimeout(5_000) { while (received.isEmpty()) delay(10) }
        assertEquals(UserSettings(themeMode = ThemeMode.DARK), received.first())
        assertEquals(0, flaky.failuresLeft)

        repository.setFontSize(FontSizeSetting.LARGE)
        withTimeout(5_000) { while (received.size < 2) delay(10) }
        collector.cancel()
        assertEquals(UserSettings(ThemeMode.DARK, FontSizeSetting.LARGE), received[1])
    }

    /**
     * ST-U-52 IOException でない例外は、リポジトリでは読み直さずにそのまま呼び出し元へ渡す（読み直せば 2 回目は読めてしまう
     * 偽物で確かめる）。受け止めて初期値で開くのは MainViewModel（ST-U-56）と SettingsViewModel（ST-U-57）
     */
    @Test
    fun nonIoError_isNotSwallowed() = runBlocking {
        val repository = open { inner -> FlakyDataStore(inner, IllegalArgumentException("作りの間違い（テスト）")) }

        val thrown = try {
            withTimeout(5_000) { repository.settings.first() }
            null
        } catch (e: IllegalArgumentException) {
            e
        }

        assertEquals("作りの間違い（テスト）", thrown?.message)
    }

    @Test
    fun retryDelay_growsUpTo5Seconds() {
        assertArrayEquals(
            longArrayOf(500, 1_000, 1_500, 5_000, 5_000),
            longArrayOf(0, 1, 2, 9, 100).map { DataStoreSettingsRepository.retryDelayMillis(it) }.toLongArray(),
        )
        assertNotEquals(0L, DataStoreSettingsRepository.retryDelayMillis(0))
    }
}

/** 最初の [failuresLeft] 回だけ読み込みで [error] を投げ、そのあとは本物に回す DataStore */
private class FlakyDataStore(
    private val inner: DataStore<Preferences>,
    private val error: Throwable,
    var failuresLeft: Int = 1,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow {
        if (failuresLeft > 0) {
            failuresLeft--
            throw error
        }
        emitAll(inner.data)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        inner.updateData(transform)
}
