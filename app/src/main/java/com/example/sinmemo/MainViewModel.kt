package com.example.sinmemo

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sinmemo.data.settings.SettingsRepository
import com.example.sinmemo.data.settings.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * アプリ全体で使う設定を読む（設定 詳細設計 5 章・10.4）。
 * 3 秒たっても読めなければ初期値で開き、遅れて読めた値・後の変更はそのまま反映する。
 */
@HiltViewModel
class MainViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {

    private val _settings = MutableStateFlow<UserSettings?>(null)

    /** 今の設定。読み終わるまで（または 3 秒たつまで）は null（その間はスプラッシュを出し続ける） */
    val settings: StateFlow<UserSettings?> = _settings.asStateFlow()

    private val _notices = MutableStateFlow<List<QueuedNotice>>(emptyList())

    /** 出すメッセージの順番待ち（通し番号付き）。出し終えたら [onNoticeShown] で外す */
    val notices: StateFlow<List<QueuedNotice>> = _notices.asStateFlow()

    private var nextNoticeId = 1L

    init {
        // スプラッシュの判定は画面を描く前から見るので、購読者を待たずにすぐ読み始める
        viewModelScope.launch {
            // 遅れて読めた値・後の変更も、そのまま入る（#59 決定 10）
            try {
                repository.settings.collect { _settings.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 読み直さない種類の失敗（IOException 以外）でも落とさない。まだ読めていなければ、初期値で開いて
                // 3 秒の打ち切りと同じ知らせを出す（二重には出さない。設定 詳細設計 10.4、開発者の決定 2026-11-17）
                Log.w(TAG, "設定を読めなかった: ${e::class.simpleName}")
                useDefaultsWithNotice()
            }
        }
        viewModelScope.launch {
            delay(SETTINGS_LOAD_TIMEOUT_MILLIS)
            // まだ読めていなければ初期値で開く。同時に読めた値は上書きしない（#59 決定 7）
            useDefaultsWithNotice()
        }
        viewModelScope.launch {
            // 壊れたファイルを上書きした知らせ。印を外してから足すので、同じ壊れで 2 度足さない（#59 決定 9）
            repository.wasReset.filter { it }.collect {
                repository.clearResetNotice()
                addNotice(SettingsNotice.Reset)
            }
        }
    }

    /** 通し番号 [id] のメッセージを出し終えた。その 1 つを外す（同じ種類が続いても、番号で見分ける） */
    fun onNoticeShown(id: Long) {
        _notices.update { list -> list.filterNot { it.id == id } }
    }

    /** まだ読めていなければ初期値にして「読み込めませんでした」を足す。もう値があれば何もしない（二重に出さない） */
    private fun useDefaultsWithNotice() {
        if (_settings.compareAndSet(null, UserSettings())) {
            addNotice(SettingsNotice.LoadTimeout)
        }
    }

    private fun addNotice(notice: SettingsNotice) {
        val queued = QueuedNotice(id = nextNoticeId++, notice = notice)
        _notices.update { it + queued }
    }

    companion object {
        /** 起動時の設定の読み込みを待つ最長の時間（#59 決定 7） */
        const val SETTINGS_LOAD_TIMEOUT_MILLIS = 3_000L

        private const val TAG = "MainViewModel"
    }
}

/** 順番待ちのメッセージ。[id] は起きた順の通し番号（画面はこれを鍵にして 1 つずつ出す） */
data class QueuedNotice(val id: Long, val notice: SettingsNotice)

/** 起動時の設定についてのメッセージ（設定 詳細設計 10.4） */
enum class SettingsNotice {
    /** 「設定を読み込めませんでした。初期値で表示します」 */
    LoadTimeout,

    /** 「設定を初期値に戻しました」 */
    Reset,
}
