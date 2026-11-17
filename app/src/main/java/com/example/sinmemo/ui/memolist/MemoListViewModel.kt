package com.example.sinmemo.ui.memolist

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sinmemo.data.memo.MemoRepository
import com.example.sinmemo.data.retryDelayMillis
import com.example.sinmemo.data.time.TimeChangeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.io.IOException
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn

/** S-01 の状態を作る（メモ一覧 詳細設計 4 章・9.2・9.4）。 */
@HiltViewModel
class MemoListViewModel internal constructor(
    repository: MemoRepository,
    clock: Clock,
    timeChanges: TimeChangeSource,
    /** 整形を動かす場所。本番は Dispatchers.Default。テストはテストの時計で動くものを渡す */
    formatDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        repository: MemoRepository,
        clock: Clock,
        timeChanges: TimeChangeSource,
    ) : this(repository, clock, timeChanges, Dispatchers.Default)

    /**
     * 一覧を読めなかったときの「メモを読み込めませんでした」の通し番号。null なら出さない（メモ一覧 詳細設計 9.4）。
     * 失敗が続いている間は 1 回だけ出す。読めたら、また出せる状態に戻す
     */
    private val _loadErrorId = MutableStateFlow<Int?>(null)
    val loadErrorId: StateFlow<Int?> = _loadErrorId.asStateFlow()
    private var nextLoadErrorId = 1

    /** 失敗が続いている間は true（画面のスレッドだけで読み書きする） */
    private var loadFailing = false

    val uiState: StateFlow<MemoListUiState> = combine(
        repository.observeSummaries(),
        // 端末のタイムゾーン・日付・時刻が変わったら、一覧が変わらなくても作り直す。
        // onStart で最初の 1 回を出し、知らせが来る前でも一覧を出せるようにする
        timeChanges.changes.onStart { emit(Unit) },
    ) { summaries, _ -> summaries }
        .map { summaries ->
            // 知らせのたびに作り直すので、新しいタイムゾーン・新しい年で出る
            val now = ZonedDateTime.now(clock)
            MemoListUiState(
                items = summaries.map { summary ->
                    MemoListItem(
                        id = summary.id,
                        title = MemoListFormatter.title(summary.head),
                        updatedAt = MemoListFormatter.updatedAt(summary.updatedAt, now),
                        updatedAtMillis = summary.updatedAt,
                    )
                },
            )
        }
        // 1,000 件の整形でも画面のスレッドを止めない
        .flowOn(formatDispatcher)
        // 読めたら、次に失敗したときにまた知らせを出せるようにする
        .onEach { loadFailing = false }
        // DB の一時的なエラーは、少し待って読み直す（その間は直前の一覧のまま。9.4）
        .retryWhen { cause, attempt ->
            if (cause.isTemporaryDbError()) {
                Log.w(TAG, "一覧を読めなかったので読み直す: ${cause::class.simpleName}")
                reportLoadFailure()
                delay(retryDelayMillis(attempt))
                true
            } else {
                false
            }
        }
        // それ以外は、何も流さず直前の一覧のまま（アプリを落とさない。9.4）
        .catch { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "一覧を読めなかった: ${e::class.simpleName}")
            reportLoadFailure()
        }
        .stateIn(
            scope = viewModelScope,
            // 画面を離れて 5 秒たったら購読を止める（画面回転では止めない）。知らせの受け手もこれに合わせて外れる
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MemoListUiState(),
        )

    /** 通し番号 [id] の知らせを出し終えた。そのあとに新しい知らせがあれば消さない */
    fun onLoadErrorShown(id: Int) {
        _loadErrorId.update { if (it == id) null else it }
    }

    /** 失敗が続いていなければ、知らせを 1 つ出す（続いている間は出さない） */
    private fun reportLoadFailure() {
        if (loadFailing) return
        loadFailing = true
        _loadErrorId.value = nextLoadErrorId++
    }

    private companion object {
        const val TAG = "MemoListViewModel"

        /** 読み直す失敗: 記憶装置の一時的なエラー（IOException）と DB のエラー（SQLiteException とその仲間） */
        fun Throwable.isTemporaryDbError(): Boolean = this is IOException || this is SQLiteException
    }
}
