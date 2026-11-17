package com.example.sinmemo.ui.memolist

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.sinmemo.data.memo.SavedMemo
import com.example.sinmemo.ui.theme.FontSize
import com.example.sinmemo.ui.theme.SinMemoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 保存して一覧に戻ったときだけ、保存したメモの行へ動かすこと（総合テストの不具合 D-1。メモ一覧 基本設計 5.1・決定事項 2、
 * メモ一覧 詳細設計 9.3）。MemoListContent に「保存したメモ」（scrollTo: id と更新日時）を渡し、一覧の位置を見る。
 */
@RunWith(RobolectricTestRunner::class)
class MemoListScrollTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var listState: LazyListState
    private var uiState by mutableStateOf(MemoListUiState())
    private var scrollTo by mutableStateOf<SavedMemo?>(null)
    private var scrolled = 0

    /** 元の更新日時は id × 1,000（id の大きいほど新しい） */
    private fun item(id: Long, updatedAt: Long = id * 1_000L) = MemoListItem(id, "メモ$id", "11/12 14:30", updatedAt)

    /** id の並び（先頭が新しい）で一覧の状態を作る */
    private fun state(vararg ids: Long) = MemoListUiState(ids.map { item(it) })

    /** 画面の lifecycle。RESUMED（表示されている）にしておき、テストで裏に回す */
    private val owner = TestLifecycleOwner()

    private fun show(initial: MemoListUiState) {
        uiState = initial
        composeRule.setContent {
            listState = rememberLazyListState()
            CompositionLocalProvider(LocalLifecycleOwner provides owner) { SinMemoTheme {
                MemoListContent(
                    uiState = uiState,
                    fontSize = FontSize.MEDIUM,
                    onOpenMemo = {}, onCreateMemo = {}, onOpenSettings = {},
                    listState = listState,
                    scrollTo = scrollTo,
                    onScrolled = {
                        scrolled++
                        scrollTo = null
                    },
                )
            } }
        }
    }

    /** メモ 50〜1（50 件、新しい順）を出し、30 行目までスクロールしておく */
    private fun showScrolled() {
        show(state(*(50L downTo 1L).toList().toLongArray()))
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(30)
        composeRule.waitForIdle()
        assertEquals(30, listState.firstVisibleItemIndex)
    }

    /** L-U-25 */
    @Test
    fun savedNew_scrollsToTop_afterItAppears() {
        showScrolled()

        // 編集画面から戻った時点では、まだ DB からの新しい一覧が届いていない
        scrollTo = SavedMemo(51, 51_000L)
        composeRule.waitForIdle()
        assertEquals("新しい一覧が届く前に動いた", 30, listState.firstVisibleItemIndex)
        assertEquals(0, scrolled)

        // 新しいメモ（51）が先頭に入った一覧が届く
        uiState = state(*(51L downTo 1L).toList().toLongArray())
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(0, listState.firstVisibleItemScrollOffset)
        composeRule.onNodeWithText("メモ51").assertIsDisplayed()
        assertEquals(1, scrolled)
    }

    /** L-U-26 */
    @Test
    fun savedExisting_scrollsToTop() {
        showScrolled()

        // 下のほうのメモ 10 を更新して保存した: 先頭に移った一覧と一緒に戻ってくる
        scrollTo = SavedMemo(10, 60_000L)
        uiState = MemoListUiState(
            listOf(item(10, 60_000L)) + (50L downTo 1L).filter { it != 10L }.map { item(it) },
        )
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        composeRule.onNodeWithText("メモ10").assertIsDisplayed()
        assertEquals(1, scrolled)
    }

    /** L-U-27 */
    @Test
    fun notSaved_keepsPosition() {
        showScrolled()

        // 保存しないで戻った（←・変更なしの ✓・破棄）: 一覧は変わらない
        composeRule.waitForIdle()
        assertEquals(30, listState.firstVisibleItemIndex)

        // 削除して戻った: 一覧から 1 件消えるが、先頭へは戻さない
        uiState = state(*(50L downTo 1L).filter { it != 45L }.toLongArray())
        composeRule.waitForIdle()

        assertTrue("先頭へ戻ってしまった", listState.firstVisibleItemIndex > 0)
        assertEquals(0, scrolled)
    }

    // ---- 7.4（#59 決定 2）先頭に来ないとき（メモ一覧 テスト設計 2.7） ----

    /** L-U-36 時計が戻っていて、保存したメモが先頭でなく 40 行目に入った */
    @Test
    fun savedNotAtTop_scrollsToItsRow() {
        showScrolled()

        // メモ 10 を書き換えたが、時計が戻っていたので、更新日時（5,500）は 6 と 5 の間
        scrollTo = SavedMemo(10, 5_500L)
        val others = (50L downTo 1L).filter { it != 10L }.map { item(it) }
        uiState = MemoListUiState(others.take(45) + item(10, 5_500L) + others.drop(45))
        assertEquals(45, uiState.items.indexOfFirst { it.id == 10L })
        composeRule.waitForIdle()

        composeRule.onNodeWithText("メモ10").assertIsDisplayed()
        assertTrue("そのメモの行まで動いていない", listState.firstVisibleItemIndex > 30)
        assertEquals(1, scrolled)
    }

    /** L-U-37 古い一覧にある同じ id（古い更新日時）では動かない */
    @Test
    fun savedExisting_waitsForNewUpdatedAt() {
        showScrolled()

        // メモ 10 を書き換えて戻った。まだ古い一覧（メモ 10 の更新日時は 10,000 のまま）
        scrollTo = SavedMemo(10, 60_000L)
        composeRule.waitForIdle()
        assertEquals("古い一覧で動いた", 30, listState.firstVisibleItemIndex)
        assertEquals(0, scrolled)

        // 更新日時が合う一覧が届いたら動く
        uiState = MemoListUiState(
            listOf(item(10, 60_000L)) + (50L downTo 1L).filter { it != 10L }.map { item(it) },
        )
        composeRule.waitForIdle()

        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(1, scrolled)
    }

    /** L-U-38 0 件から 1 件 */
    @Test
    fun firstMemo_fromEmptyList_isShown() {
        show(MemoListUiState())
        composeRule.waitForIdle()

        scrollTo = SavedMemo(1, 1_000L)
        uiState = state(1L)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("メモ1").assertIsDisplayed()
        assertEquals(0, listState.firstVisibleItemIndex)
        assertEquals(1, scrolled)
    }

    /** L-U-39 保存したメモが入らないまま、表示されている時間で 2 秒たったら、位置を変えずにあきらめる */
    @Test
    fun savedNeverAppears_givesUpAfter2Seconds() {
        showScrolled()

        scrollTo = SavedMemo(51, 51_000L)
        composeRule.waitForIdle()
        // 画面の時計（テストの仮想の時間）だけで進める。待つ処理が始まるまでに画面の時計がいくらか進むことがあるので、
        // 2 秒の前後に 0.5 秒ずつのゆとりを取る
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.waitForIdle()
        assertEquals("2 秒より前にあきらめた", 0, scrolled)

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertEquals(1, scrolled)
        assertEquals(30, listState.firstVisibleItemIndex)
    }

    /**
     * L-U-45 裏にいる（RESUMED でない）間は 2 秒を数えない。✓ の直後にホームへ戻り、しばらくしてから戻ったときも、
     * 保存したメモが入ればその行へ動く（開発者の決定 2026-11-17）
     */
    @Test
    fun whileNotResumed_waitIsNotCounted() {
        showScrolled()

        scrollTo = SavedMemo(51, 51_000L)
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        composeRule.waitForIdle()
        // 裏にいる間に 10 秒たっても、あきらめない
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        assertEquals("裏にいる間にあきらめた", 0, scrolled)

        // 戻ってから 1 秒（2 秒より前）
        composeRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(0, scrolled)

        // 新しいメモが入った一覧が届くと、先頭へ動く
        uiState = state(*(51L downTo 1L).toList().toLongArray())
        composeRule.waitForIdle()
        assertEquals(1, scrolled)
        assertEquals(0, listState.firstVisibleItemIndex)
    }

    /** L-U-46 一覧を読めなかった知らせがあると「メモを読み込めませんでした」を出し、番号を返す */
    @Test
    fun loadError_showsMessage_andReports() {
        val shown = mutableListOf<Int>()
        composeRule.setContent {
            SinMemoTheme {
                MemoListContent(
                    uiState = state(1L),
                    fontSize = FontSize.MEDIUM,
                    onOpenMemo = {}, onCreateMemo = {}, onOpenSettings = {},
                    loadErrorId = 3,
                    onLoadErrorShown = { shown += it },
                )
            }
        }

        composeRule.onNodeWithText("メモを読み込めませんでした").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        assertEquals(listOf(3), shown)
    }
}

/** テスト用の lifecycle。最初から RESUMED */
private class TestLifecycleOwner : LifecycleOwner {
    val registry: LifecycleRegistry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}
