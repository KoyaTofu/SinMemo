package com.example.sinmemo

import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.data.settings.SettingsRepository
import com.example.sinmemo.data.settings.ThemeMode
import com.example.sinmemo.data.settings.UserSettings
import com.example.sinmemo.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * MainViewModel（設定 詳細設計 5 章・10.4）。読み終わるまで null、読んだら値、変わったら追従。
 * 3 秒の打ち切りは、Dispatchers.Main をテストの仮想の時間で動く StandardTestDispatcher にして確かめる。
 */
class MainViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    /** 値を流すまで「読み込み中」のままにできる偽物 */
    private class SlowRepository : SettingsRepository {
        val flow = MutableSharedFlow<UserSettings>(replay = 1)
        val resetFlag = MutableStateFlow(false)
        var clearCount = 0
        override val settings: Flow<UserSettings> = flow
        override val wasReset: StateFlow<Boolean> = resetFlag
        override suspend fun setThemeMode(mode: ThemeMode) = Unit
        override suspend fun setFontSize(size: FontSizeSetting) = Unit
        override fun clearResetNotice() {
            clearCount++
            resetFlag.value = false
        }
    }

    private val dark = UserSettings(ThemeMode.DARK, FontSizeSetting.LARGE)

    /** 同じ仮想の時間で動かす（viewModelScope は Dispatchers.Main = テストの dispatcher） */
    private fun test(body: suspend TestScope.() -> Unit) = runTest(mainDispatcherRule.dispatcher) { body() }

    @Test
    fun settings_isNull_untilLoaded_thenFollowsChanges() = test {
        val repository = SlowRepository()
        val viewModel = MainViewModel(repository)
        runCurrent()

        assertNull(viewModel.settings.value)

        repository.flow.emit(dark)
        runCurrent()
        assertEquals(dark, viewModel.settings.value)

        repository.flow.emit(UserSettings(ThemeMode.SYSTEM, FontSizeSetting.SMALL))
        runCurrent()
        assertEquals(UserSettings(ThemeMode.SYSTEM, FontSizeSetting.SMALL), viewModel.settings.value)
    }

    /** ST-U-44 */
    @Test
    fun loadedWithin3Seconds_noNotice() = test {
        val repository = SlowRepository()
        val viewModel = MainViewModel(repository)
        runCurrent()

        advanceTimeBy(2_999)
        repository.flow.emit(dark)
        runCurrent()
        assertEquals(dark, viewModel.settings.value)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(dark, viewModel.settings.value)
        assertEquals(emptyList<SettingsNotice>(), viewModel.notices.value.map { it.notice })
    }

    /** ST-U-45 */
    @Test
    fun notLoadedIn3Seconds_usesDefaults_withNotice() = test {
        val viewModel = MainViewModel(SlowRepository())
        runCurrent()

        advanceTimeBy(2_999)
        runCurrent()
        assertNull(viewModel.settings.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(UserSettings(), viewModel.settings.value)
        assertEquals(listOf(SettingsNotice.LoadTimeout), viewModel.notices.value.map { it.notice })
    }

    /** ST-U-46 */
    @Test
    fun lateLoad_afterTimeout_switchesToLoadedValue() = test {
        val repository = SlowRepository()
        val viewModel = MainViewModel(repository)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(UserSettings(), viewModel.settings.value)

        advanceTimeBy(2_000)
        repository.flow.emit(dark)
        runCurrent()

        assertEquals(dark, viewModel.settings.value)
        // メッセージは取り消さない
        assertEquals(listOf(SettingsNotice.LoadTimeout), viewModel.notices.value.map { it.notice })
    }

    /** ST-U-47 */
    @Test
    fun reset_addsNoticeOnce_andClearsFlag() = test {
        val repository = SlowRepository()
        val viewModel = MainViewModel(repository)
        repository.flow.emit(UserSettings())
        runCurrent()

        repository.resetFlag.value = true
        runCurrent()
        assertEquals(listOf(SettingsNotice.Reset), viewModel.notices.value.map { it.notice })
        assertFalse(repository.resetFlag.value)
        assertEquals(1, repository.clearCount)

        // 同じ壊れで 2 度足されない（時間が進んでも増えない）
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(SettingsNotice.Reset), viewModel.notices.value.map { it.notice })

        // 別の壊れで、もう一度 true になったときは 1 つ足される
        repository.resetFlag.value = true
        runCurrent()
        assertEquals(listOf(SettingsNotice.Reset, SettingsNotice.Reset), viewModel.notices.value.map { it.notice })
        // 同じ種類でも番号は別（画面は番号で見分ける）
        assertEquals(2, viewModel.notices.value.map { it.id }.toSet().size)
    }

    /** ST-U-48 */
    @Test
    fun noticeShown_removesIt_inOrder() = test {
        val repository = SlowRepository()
        val viewModel = MainViewModel(repository)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        repository.resetFlag.value = true
        runCurrent()
        assertEquals(listOf(SettingsNotice.LoadTimeout, SettingsNotice.Reset), viewModel.notices.value.map { it.notice })

        val (timeout, reset) = viewModel.notices.value
        // 通し番号は起きた順
        assertTrue(timeout.id < reset.id)

        // ない番号を外そうとしても何も外れない
        viewModel.onNoticeShown(999L)
        assertEquals(listOf(SettingsNotice.LoadTimeout, SettingsNotice.Reset), viewModel.notices.value.map { it.notice })

        viewModel.onNoticeShown(timeout.id)
        assertEquals(listOf(SettingsNotice.Reset), viewModel.notices.value.map { it.notice })
        viewModel.onNoticeShown(reset.id)
        assertEquals(emptyList<SettingsNotice>(), viewModel.notices.value.map { it.notice })
    }

    /** ST-U-56 読み込みで IOException 以外の例外が起きても落ちず、初期値で開いて「読み込めませんでした」を 1 回だけ出す */
    @Test
    fun nonIoError_usesDefaults_withOneNotice() = test {
        val repository = object : SettingsRepository by SlowRepository() {
            override val settings: Flow<UserSettings> = flow { throw IllegalArgumentException("作りの間違い（テスト）") }
        }
        val viewModel = MainViewModel(repository)
        runCurrent()

        assertEquals(UserSettings(), viewModel.settings.value)
        assertEquals(listOf(SettingsNotice.LoadTimeout), viewModel.notices.value.map { it.notice })

        // 3 秒の打ち切りが来ても、二重には出さない
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(listOf(SettingsNotice.LoadTimeout), viewModel.notices.value.map { it.notice })
    }

    /** ST-U-56 の続き: 読めたあとで例外が起きたときは、読めた値のままで、知らせも出さない */
    @Test
    fun nonIoError_afterLoad_keepsValue_withoutNotice() = test {
        val repository = object : SettingsRepository by SlowRepository() {
            override val settings: Flow<UserSettings> = flow {
                emit(dark)
                throw IllegalArgumentException("作りの間違い（テスト）")
            }
        }
        val viewModel = MainViewModel(repository)
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(dark, viewModel.settings.value)
        assertEquals(emptyList<SettingsNotice>(), viewModel.notices.value.map { it.notice })
    }
}
