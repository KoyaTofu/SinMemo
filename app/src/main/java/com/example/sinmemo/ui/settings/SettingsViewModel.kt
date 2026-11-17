package com.example.sinmemo.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sinmemo.data.settings.FontSizeSetting
import com.example.sinmemo.data.settings.SettingsRepository
import com.example.sinmemo.data.settings.ThemeMode
import com.example.sinmemo.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * S-03・D-03・D-04（設定 基本設計 3 章、詳細設計 7 章）。
 * ダイアログの開閉と印の位置はここで持つので、画面を回転しても消えない。保存は「OK」のときだけ。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
) : ViewModel() {

    private val dialog = MutableStateFlow<SettingsDialog?>(null)

    /** 保存している設定。ダイアログを開いたときの印の初期位置にも使う */
    private val settings: StateFlow<UserSettings> = repository.settings
        // 読めない（IOException 以外の失敗）ときも落とさず、初期値で表示する（設定 詳細設計 10.6）
        .catch { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "設定を読めなかった: ${e::class.simpleName}")
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val uiState: StateFlow<SettingsUiState> = combine(settings, dialog) { settings, dialog ->
        SettingsUiState(settings = settings, dialog = dialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** 「テーマ」を押した: 今の値に印を付けて D-03 を開く */
    fun onThemeClick() {
        dialog.value = SettingsDialog.Theme(selected = settings.value.themeMode)
    }

    /** 「文字サイズ」を押した: 今の値に印を付けて D-04 を開く */
    fun onFontSizeClick() {
        dialog.value = SettingsDialog.FontSize(selected = settings.value.fontSize)
    }

    /** D-03 の選択肢を押した: 印を動かすだけ（保存しない） */
    fun onThemeSelect(mode: ThemeMode) {
        dialog.update { if (it is SettingsDialog.Theme) it.copy(selected = mode) else it }
    }

    /** D-04 の選択肢を押した: 印を動かすだけ（保存しない） */
    fun onFontSizeSelect(size: FontSizeSetting) {
        dialog.update { if (it is SettingsDialog.FontSize) it.copy(selected = size) else it }
    }

    /** 「OK」: ダイアログを閉じて、印の値を保存する（保存するとアプリ全体に反映される） */
    fun onDialogConfirm() {
        val current = dialog.value ?: return
        dialog.value = null
        viewModelScope.launch {
            try {
                when (current) {
                    is SettingsDialog.Theme -> repository.setThemeMode(current.selected)
                    is SettingsDialog.FontSize -> repository.setFontSize(current.selected)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 保存の失敗は表示を元の値のまま、メッセージも出さない。アプリは落とさない（設定 詳細設計 10.6）
                Log.w(TAG, "設定を保存できなかった: ${e::class.simpleName}")
            }
        }
    }

    /** 「キャンセル」／ダイアログの外を押す／戻る操作: 何も変えずに閉じる */
    fun onDialogDismiss() {
        dialog.value = null
    }

    private companion object {
        const val TAG = "SettingsViewModel"
    }
}
