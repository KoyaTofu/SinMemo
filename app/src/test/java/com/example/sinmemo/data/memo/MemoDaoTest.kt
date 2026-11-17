package com.example.sinmemo.data.memo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.data.db.SinMemoDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** MemoDao の単体テスト（共通 詳細設計 11 章、共通 テスト設計 2.1）。Robolectric で PC 上で動かす。 */
@RunWith(RobolectricTestRunner::class)
class MemoDaoTest {

    private lateinit var db: SinMemoDatabase
    private lateinit var dao: MemoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SinMemoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.memoDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun memo(body: String, createdAt: Long = 1_000L, updatedAt: Long = createdAt) =
        Memo(body = body, createdAt = createdAt, updatedAt = updatedAt)

    @Test
    fun insert_assignsIdFromOne_andGetByIdReturnsSameMemo() = runTest {
        val id = dao.insert(memo("牛乳\n卵", createdAt = 100L, updatedAt = 200L))

        assertEquals(1L, id)
        assertEquals(
            Memo(id = 1L, body = "牛乳\n卵", createdAt = 100L, updatedAt = 200L),
            dao.getById(id),
        )
    }

    @Test
    fun insert_neverUsesNewMemoId() = runTest {
        val ids = (1..3).map { dao.insert(memo("メモ$it")) }

        assertTrue(ids.none { it == NEW_MEMO_ID })
    }

    @Test
    fun getById_returnsNull_whenNotFound() = runTest {
        assertNull(dao.getById(999L))
    }

    @Test
    fun updateBody_changesBodyAndUpdatedAt_keepsCreatedAt() = runTest {
        val id = dao.insert(memo("前", createdAt = 100L))

        val count = dao.updateBody(id = id, body = "後", updatedAt = 500L)

        assertEquals(1, count)
        assertEquals(Memo(id = id, body = "後", createdAt = 100L, updatedAt = 500L), dao.getById(id))
    }

    @Test
    fun updateBody_returnsZero_whenNotFound() = runTest {
        assertEquals(0, dao.updateBody(id = 999L, body = "x", updatedAt = 1L))
    }

    @Test
    fun deleteById_removesOnlyThatMemo() = runTest {
        val keep = dao.insert(memo("残す"))
        val remove = dao.insert(memo("消す"))

        assertEquals(1, dao.deleteById(remove))
        assertNull(dao.getById(remove))
        assertEquals(listOf(keep), dao.observeSummaries().first().map { it.id })
    }

    @Test
    fun deleteById_returnsZero_whenNotFound() = runTest {
        assertEquals(0, dao.deleteById(999L))
    }

    @Test
    fun observeSummaries_ordersByUpdatedAtDesc_thenIdDesc() = runTest {
        val old = dao.insert(memo("古い", updatedAt = 100L))
        val newest = dao.insert(memo("最新", updatedAt = 300L))
        val sameTimeFirst = dao.insert(memo("同時刻1", updatedAt = 200L))
        val sameTimeSecond = dao.insert(memo("同時刻2", updatedAt = 200L))

        val ids = dao.observeSummaries().first().map { it.id }

        assertEquals(listOf(newest, sameTimeSecond, sameTimeFirst, old), ids)
    }

    @Test
    fun observeSummaries_returnsEmptyList_whenNoMemos() = runTest {
        assertEquals(emptyList<MemoSummary>(), dao.observeSummaries().first())
    }

    @Test
    fun observeSummaries_cutsHeadToHeadLength_countingCharacters() = runTest {
        // 日本語（UTF-8 で 3 バイト）でも「文字数」で切られることを確かめる
        val long = "あ".repeat(MemoSummary.HEAD_LENGTH + 50)
        dao.insert(memo(long, updatedAt = 2L))
        dao.insert(memo("短いメモ", updatedAt = 1L))

        val summaries = dao.observeSummaries().first()

        assertEquals("あ".repeat(MemoSummary.HEAD_LENGTH), summaries[0].head)
        assertEquals("短いメモ", summaries[1].head)
    }

    @Test
    fun observeSummaries_carriesUpdatedAt() = runTest {
        val id = dao.insert(memo("x", createdAt = 10L, updatedAt = 20L))

        assertEquals(MemoSummary(id = id, head = "x", updatedAt = 20L), dao.observeSummaries().first().single())
    }

    @Test
    fun observeSummaries_emitsAgain_whenTableChanges() {
        // Room は別スレッドで変更を通知するので、仮想時間の runTest ではなく実時間で待つ
        runBlocking {
            withTimeout(5_000) {
                val received = Channel<List<MemoSummary>>(Channel.UNLIMITED)
                val job = launch { dao.observeSummaries().collect { received.send(it) } }

                // 1 回目（空の一覧）を受け取ってから追加する
                assertEquals(emptyList<MemoSummary>(), received.receive())
                dao.insert(memo("追加"))
                assertEquals(listOf("追加"), received.receive().map { it.head })

                job.cancel()
            }
        }
    }
}
