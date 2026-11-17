package com.example.sinmemo.ui.memolist

import com.example.sinmemo.data.memo.FakeMemoRepository
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.data.memo.MemoRepository
import com.example.sinmemo.data.memo.MemoSummary
import com.example.sinmemo.data.time.FakeTimeChangeSource
import com.example.sinmemo.testutil.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * MemoListViewModel の単体テスト。偽のリポジトリ、偽の時刻の変更の知らせと、
 * タイムゾーンと時刻を途中で変えられる時計（初めは UTC 2026-11-12 15:00）を使う。
 */
class MemoListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    /** タイムゾーンと時刻を途中で変えられる時計 */
    private class MutableClock(var instant: Instant, var zoneId: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
        override fun instant(): Instant = instant
    }

    private val clock = MutableClock(LocalDateTime.parse("2026-11-12T15:00").toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val timeChanges = FakeTimeChangeSource()

    private fun millis(text: String) = LocalDateTime.parse(text).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun memo(id: Long, body: String, updated: String) =
        Memo(id = id, body = body, createdAt = millis(updated), updatedAt = millis(updated))

    /** 作った ViewModel は、テストの終わりに片付ける（[vmTest]） */
    private val store = ViewModelStore()
    private val scopes = mutableListOf<Job>()

    private fun newViewModel(repository: MemoRepository): MemoListViewModel {
        // 整形もテストの dispatcher で動かす（別スレッドを使わず、テストの仮想の時間だけで進む）
        val factory = viewModelFactory {
            initializer { MemoListViewModel(repository, clock, timeChanges, mainDispatcherRule.dispatcher) }
        }
        return ViewModelProvider(store, factory)["vm${scopes.size}", MemoListViewModel::class.java].also {
            scopes += it.viewModelScope.coroutineContext.job
        }
    }

    /**
     * runTest に、終わりの片付けを足したもの。テストの中で ViewModel を片付け（購読と読み直しの待ちを止め）、
     * 終わりきるのを待つ。Dispatchers.Main が外されたあとに ViewModel の処理が残らないようにする。
     */
    private fun vmTest(body: suspend TestScope.() -> Unit): TestResult = runTest {
        try {
            body()
        } finally {
            store.clear()
            scopes.forEach { it.join() }
        }
    }

    /**
     * 期待する件数になるまで待って取り出す（整形はテストの dispatcher で動く）。
     * runTest の中の withTimeout は仮想時間で進み、別スレッドの処理を待たずに切れることがあるため使わない
     * （待ちすぎたときは runTest 自体のタイムアウトで失敗する）。
     */
    private suspend fun MemoListViewModel.awaitItems(count: Int): List<MemoListItem> =
        uiState.first { it.items.size == count }.items

    /** 知らせを受けられるよう、uiState を購読しておく（WhileSubscribed のため） */
    private fun TestScope.subscribe(viewModel: MemoListViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun uiState_isEmpty_whenNoMemos() = vmTest {
        val viewModel = newViewModel(FakeMemoRepository())

        assertEquals(emptyList<MemoListItem>(), viewModel.awaitItems(0))
    }

    @Test
    fun uiState_convertsSummariesToItems_inNewestOrder() = vmTest {
        val repository = FakeMemoRepository().apply {
            put(
                memo(1, "旅行の持ち物", "2025-12-28T21:04"),
                memo(2, "\n\n打ち合わせメモ\n議題", "2026-11-10T09:15"),
                memo(3, "牛乳、卵\nパン", "2026-11-12T14:30"),
                memo(4, "年始の目標", "2026-01-05T09:03"),
            )
        }
        val viewModel = newViewModel(repository)

        assertEquals(
            listOf(
                MemoListItem(3, "牛乳、卵", "11/12 14:30", millis("2026-11-12T14:30")),
                MemoListItem(2, "打ち合わせメモ", "11/10 09:15", millis("2026-11-10T09:15")),
                MemoListItem(4, "年始の目標", "01/05 09:03", millis("2026-01-05T09:03")),
                MemoListItem(1, "旅行の持ち物", "2025/12/28 21:04", millis("2025-12-28T21:04")),
            ),
            viewModel.awaitItems(4),
        )
    }

    @Test
    fun uiState_followsRepositoryChanges() = vmTest {
        val repository = FakeMemoRepository(nowMillis = millis("2026-11-12T10:00"))
        val viewModel = newViewModel(repository)
        assertEquals(0, viewModel.awaitItems(0).size)

        // 追加
        val first = repository.create("1 件目").id
        repository.nowMillis = millis("2026-11-12T11:00")
        val second = repository.create("2 件目").id
        assertEquals(listOf(second, first), viewModel.awaitItems(2).map { it.id })

        // 書き直すと先頭へ（US-03）
        repository.nowMillis = millis("2026-11-12T12:00")
        repository.update(first, "1 件目（書き直し）")
        val updated = viewModel.uiState.first { it.items.firstOrNull()?.id == first }.items
        assertEquals(MemoListItem(first, "1 件目（書き直し）", "11/12 12:00", millis("2026-11-12T12:00")), updated.first())

        // 削除
        repository.delete(second)
        assertEquals(listOf(first), viewModel.awaitItems(1).map { it.id })
    }

    // ---- 7.4（#59）時刻の変更・読み込みの失敗（メモ一覧 テスト設計 2.7） ----

    /** L-U-31 */
    @Test
    fun timezoneChange_reformatsDates() = vmTest {
        val repository = FakeMemoRepository().apply { put(memo(1, "牛乳", "2026-11-12T15:00")) }
        val viewModel = newViewModel(repository)
        subscribe(viewModel)
        assertEquals("11/12 15:00", viewModel.awaitItems(1).single().updatedAt)

        clock.zoneId = ZoneId.of("Asia/Tokyo")
        timeChanges.notifyChanged()

        val item = viewModel.uiState.first { it.items.single().updatedAt != "11/12 15:00" }.items.single()
        assertEquals("11/13 00:00", item.updatedAt)
        // 一覧（DB）は変えていない
        assertEquals(millis("2026-11-12T15:00"), item.updatedAtMillis)
    }

    /** L-U-32 */
    @Test
    fun dateChange_toNewYear_addsYear() = vmTest {
        clock.instant = LocalDateTime.parse("2026-12-31T23:59").toInstant(ZoneOffset.UTC)
        val repository = FakeMemoRepository().apply { put(memo(1, "大みそか", "2026-12-31T10:00")) }
        val viewModel = newViewModel(repository)
        subscribe(viewModel)
        assertEquals("12/31 10:00", viewModel.awaitItems(1).single().updatedAt)

        clock.instant = LocalDateTime.parse("2027-01-01T00:00").toInstant(ZoneOffset.UTC)
        timeChanges.notifyChanged()

        val item = viewModel.uiState.first { it.items.single().updatedAt != "12/31 10:00" }.items.single()
        assertEquals("2026/12/31 10:00", item.updatedAt)
    }

    /** L-U-33 知らせがなければ作り直さない（知らせで動くことの比較） */
    @Test
    fun noTimeChange_noReformat() = vmTest {
        val repository = FakeMemoRepository().apply { put(memo(1, "牛乳", "2026-11-12T15:00")) }
        val viewModel = newViewModel(repository)
        subscribe(viewModel)
        assertEquals("11/12 15:00", viewModel.awaitItems(1).single().updatedAt)

        clock.zoneId = ZoneId.of("Asia/Tokyo")
        // 待っている処理をすべて進めても（知らせがないので）作り直さない
        advanceUntilIdle()

        assertEquals("11/12 15:00", viewModel.uiState.value.items.single().updatedAt)
    }

    /** L-U-34 */
    @Test
    fun items_carryUpdatedAtMillis() = vmTest {
        val repository = FakeMemoRepository().apply {
            put(memo(1, "a", "2026-11-12T10:00"), memo(2, "b", "2025-01-01T00:00"))
        }

        val items = newViewModel(repository).awaitItems(2)

        assertEquals(listOf(millis("2026-11-12T10:00"), millis("2025-01-01T00:00")), items.map { it.updatedAtMillis })
    }

    /** L-U-35 一覧の流れが例外で止まっても、直前の一覧のまま。テストが例外で落ちない */
    @Test
    fun repositoryError_keepsLastList() = vmTest {
        val failNow = CompletableDeferred<Unit>()
        val thrown = CompletableDeferred<Unit>()
        val repository = object : MemoRepository by FakeMemoRepository() {
            override fun observeSummaries(): Flow<List<MemoSummary>> = flow {
                emit(listOf(MemoSummary(1, "牛乳", millis("2026-11-12T10:00"))))
                failNow.await()
                thrown.complete(Unit)
                throw IllegalStateException("DB を読めなかった（テスト）")
            }
        }
        val viewModel = newViewModel(repository)
        subscribe(viewModel)
        assertEquals(1, viewModel.awaitItems(1).size)

        failNow.complete(Unit)
        thrown.await()
        // 例外が下流へ届いて catch されるまで進める
        advanceUntilIdle()

        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.id })
    }

    /** L-U-44 DB の一時的なエラー（SQLiteException）は少し待って読み直し、そのあとの変更も流れてくる */
    @Test
    fun temporaryDbError_retries_andFollowsLaterChanges() = vmTest {
        val inner = FakeMemoRepository(nowMillis = millis("2026-11-12T10:00")).apply { put(memo(1, "牛乳", "2026-11-12T09:00")) }
        var failuresLeft = 1
        val repository = object : MemoRepository by inner {
            override fun observeSummaries(): Flow<List<MemoSummary>> = flow {
                if (failuresLeft > 0) {
                    failuresLeft--
                    throw SQLiteException("一時的なエラー（テスト）")
                }
                emitAll(inner.observeSummaries())
            }
        }
        val viewModel = newViewModel(repository)
        subscribe(viewModel)
        runCurrent()
        assertEquals("読み直す前に一覧が出た", 0, viewModel.uiState.value.items.size)

        // 0.5 秒待ってから読み直す
        advanceTimeBy(499)
        runCurrent()
        assertEquals(0, viewModel.uiState.value.items.size)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.id })

        inner.create("卵")
        runCurrent()
        assertEquals(2, viewModel.uiState.value.items.size)
    }

    /**
     * L-U-47 一覧を読めなかったら「メモを読み込めませんでした」の知らせを出す。失敗が続いている間は 1 回だけ。
     * 読めたら、次の失敗でまた出す（開発者の決定 2026-11-17）
     */
    @Test
    fun loadError_isReportedOncePerFailureStreak() = vmTest {
        var collections = 0
        val inner = FakeMemoRepository().apply { put(memo(1, "牛乳", "2026-11-12T09:00")) }
        val repository = object : MemoRepository by inner {
            override fun observeSummaries(): Flow<List<MemoSummary>> = flow {
                collections++
                when (collections) {
                    // 1 回目: 読めたあとに失敗 → 2 回目: すぐ失敗（続いている） → 3 回目: 読めたあとに失敗（新しい失敗）
                    1, 3 -> {
                        emit(inner.observeSummaries().first())
                        throw SQLiteException("一時的なエラー（テスト）")
                    }
                    2 -> throw SQLiteException("一時的なエラー（テスト）")
                    else -> emitAll(inner.observeSummaries())
                }
            }
        }
        val viewModel = newViewModel(repository)
        val ids = mutableListOf<Int?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.loadErrorId.collect { ids += it } }
        subscribe(viewModel)

        advanceUntilIdle()

        assertEquals(4, collections)
        assertEquals("続いている間に 2 回出した、または読めたあとに出さなかった", listOf(null, 1, 2), ids)
        assertEquals(listOf(1L), viewModel.uiState.value.items.map { it.id })

        // 古い番号では消えない。今の番号で消える
        viewModel.onLoadErrorShown(1)
        assertEquals(2, viewModel.loadErrorId.value)
        viewModel.onLoadErrorShown(2)
        assertEquals(null, viewModel.loadErrorId.value)
    }
}
