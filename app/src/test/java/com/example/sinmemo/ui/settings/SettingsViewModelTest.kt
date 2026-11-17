package com.example.sinmemo.ui.settings

import com.example.sinmemo.data.settings.FakeSettingsRepository
import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.data.settings.SettingsRepository
import com.example.sinmemo.data.settings.ThemeMode
import com.example.sinmemo.data.settings.UserSettings
import com.example.sinmemo.testutil.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** SettingsViewModel（設定 基本設計 3 章の表）。偽のリポジトリを使う。 */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeSettingsRepository(UserSettings(ThemeMode.LIGHT, FontSizeSetting.MEDIUM))

    /**
     * uiState は画面が見ている間だけ動く（WhileSubscribed）ので、テストでも購読しておく。
     * 値がすぐ届くよう、テストの時計を共有する UnconfinedTestDispatcher で購読する。
     */
    private fun TestScope.createViewModel() = SettingsViewModel(repository).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun initialState_showsSavedSettings_andNoDialog() = runTest {
        repository.state.value = UserSettings(ThemeMode.DARK, FontSizeSetting.SMALL)
        val viewModel = createViewModel()

        assertEquals(SettingsUiState(UserSettings(ThemeMode.DARK, FontSizeSetting.SMALL), null), viewModel.uiState.value)
    }

    @Test
    fun themeClick_opensD03_withCurrentValueSelected() = runTest {
        repository.state.value = UserSettings(themeMode = ThemeMode.SYSTEM)
        val viewModel = createViewModel()

        viewModel.onThemeClick()

        assertEquals(SettingsDialog.Theme(ThemeMode.SYSTEM), viewModel.uiState.value.dialog)
    }

    @Test
    fun fontSizeClick_opensD04_withCurrentValueSelected() = runTest {
        repository.state.value = UserSettings(fontSize = FontSizeSetting.LARGE)
        val viewModel = createViewModel()

        viewModel.onFontSizeClick()

        assertEquals(SettingsDialog.FontSize(FontSizeSetting.LARGE), viewModel.uiState.value.dialog)
    }

    @Test
    fun select_movesMark_butDoesNotSave() = runTest {
        val viewModel = createViewModel()
        viewModel.onThemeClick()

        viewModel.onThemeSelect(ThemeMode.DARK)

        assertEquals(SettingsDialog.Theme(ThemeMode.DARK), viewModel.uiState.value.dialog)
        assertEquals(ThemeMode.LIGHT, repository.state.value.themeMode)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun themeOk_savesSelected_andCloses() = runTest {
        val viewModel = createViewModel()
        viewModel.onThemeClick()
        viewModel.onThemeSelect(ThemeMode.DARK)

        viewModel.onDialogConfirm()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(ThemeMode.DARK, repository.state.value.themeMode)
        // 画面の「今の値」も変わる
        assertEquals(ThemeMode.DARK, viewModel.uiState.value.settings.themeMode)
        // 文字サイズは変えない
        assertEquals(FontSizeSetting.MEDIUM, repository.state.value.fontSize)
    }

    @Test
    fun fontSizeOk_savesSelected_andCloses() = runTest {
        val viewModel = createViewModel()
        viewModel.onFontSizeClick()
        viewModel.onFontSizeSelect(FontSizeSetting.SMALL)

        viewModel.onDialogConfirm()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(UserSettings(ThemeMode.LIGHT, FontSizeSetting.SMALL), repository.state.value)
    }

    @Test
    fun cancel_closes_withoutSaving() = runTest {
        val viewModel = createViewModel()
        viewModel.onThemeClick()
        viewModel.onThemeSelect(ThemeMode.DARK)

        viewModel.onDialogDismiss()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(ThemeMode.LIGHT, repository.state.value.themeMode)
        assertEquals(0, repository.saveCount)
    }

    @Test
    fun reopen_afterCancel_startsFromSavedValue() = runTest {
        val viewModel = createViewModel()
        viewModel.onFontSizeClick()
        viewModel.onFontSizeSelect(FontSizeSetting.LARGE)
        viewModel.onDialogDismiss()

        viewModel.onFontSizeClick()

        assertEquals(SettingsDialog.FontSize(FontSizeSetting.MEDIUM), viewModel.uiState.value.dialog)
    }

    @Test
    fun okWithoutChange_keepsValue_andCloses() = runTest {
        val viewModel = createViewModel()
        viewModel.onThemeClick()

        viewModel.onDialogConfirm()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(UserSettings(ThemeMode.LIGHT, FontSizeSetting.MEDIUM), repository.state.value)
    }

    @Test
    fun selectOfOtherKind_isIgnored() = runTest {
        val viewModel = createViewModel()
        viewModel.onThemeClick()

        viewModel.onFontSizeSelect(FontSizeSetting.LARGE)

        assertEquals(SettingsDialog.Theme(ThemeMode.LIGHT), viewModel.uiState.value.dialog)
    }

    @Test
    fun confirm_withoutDialog_doesNothing() = runTest {
        val viewModel = createViewModel()

        viewModel.onDialogConfirm()

        assertEquals(0, repository.saveCount)
    }

    /** ST-U-53 保存で IOException が出ても落ちず、ダイアログは閉じ、値は元のまま（設定 詳細設計 10.6） */
    @Test
    fun saveFailure_keepsValue_withoutCrash() = runTest {
        repository.failWrites = true
        val viewModel = createViewModel()
        viewModel.onThemeClick()
        viewModel.onThemeSelect(ThemeMode.DARK)

        viewModel.onDialogConfirm()

        assertEquals(1, repository.saveCount)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(UserSettings(ThemeMode.LIGHT, FontSizeSetting.MEDIUM), viewModel.uiState.value.settings)
    }

    /** ST-U-57 設定画面でも、読み込みで IOException 以外の例外が起きても落ちず、初期値で表示する */
    @Test
    fun nonIoError_showsDefaults_withoutCrash() = runTest {
        val failing = object : SettingsRepository by repository {
            override val settings: Flow<UserSettings> = flow { throw IllegalArgumentException("作りの間違い（テスト）") }
        }
        val viewModel = SettingsViewModel(failing)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(SettingsUiState(UserSettings(), null), viewModel.uiState.value)
    }
}
