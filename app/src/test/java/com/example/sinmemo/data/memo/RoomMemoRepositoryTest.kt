package com.example.sinmemo.data.memo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.data.db.SinMemoDatabase
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** RoomMemoRepository の単体テスト。時刻を固定して、作成日時・更新日時が正しく入ることを確かめる。 */
@RunWith(RobolectricTestRunner::class)
class RoomMemoRepositoryTest {

    /** テストから進められる時計。[step] を 0 より大きくすると、読むたびにその分だけ進む */
    private class TestClock(var nowMillis: Long, var step: Long = 0L) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis).also { nowMillis += step }
    }

    private lateinit var db: SinMemoDatabase
    private lateinit var clock: TestClock
    private lateinit var repository: RoomMemoRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SinMemoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        clock = TestClock(nowMillis = 1_000L)
        repository = RoomMemoRepository(db.memoDao(), clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun create_setsCreatedAtAndUpdatedAtToNow() = runTest {
        clock.nowMillis = 1_700_000_000_000L

        val id = repository.create("買い物").id

        assertEquals(
            Memo(id = id, body = "買い物", createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_000_000L),
            repository.getMemo(id),
        )
    }

    @Test
    fun update_changesBodyAndUpdatedAt_keepsCreatedAt() = runTest {
        clock.nowMillis = 1_000L
        val id = repository.create("前").id

        clock.nowMillis = 5_000L
        repository.update(id, "後")

        assertEquals(Memo(id = id, body = "後", createdAt = 1_000L, updatedAt = 5_000L), repository.getMemo(id))
    }

    @Test
    fun update_movesMemoToTopOfList() = runTest {
        clock.nowMillis = 1_000L
        val first = repository.create("1 件目").id
        clock.nowMillis = 2_000L
        val second = repository.create("2 件目").id

        clock.nowMillis = 3_000L
        repository.update(first, "1 件目（書き直し）")

        assertEquals(listOf(first, second), repository.observeSummaries().first().map { it.id })
    }

    @Test
    fun delete_removesMemo() = runTest {
        val id = repository.create("消す").id

        repository.delete(id)

        assertNull(repository.getMemo(id))
        assertEquals(emptyList<MemoSummary>(), repository.observeSummaries().first())
    }

    @Test
    fun getMemo_returnsNull_whenNotFound() = runTest {
        assertNull(repository.getMemo(999L))
    }

    // ---- 7.4（#59）戻り値と失敗（共通 テスト設計 2.4） ----

    /** C-U-18 */
    @Test
    fun create_returnsIdAndUpdatedAt() = runTest {
        // 読むたびに進む時計。時刻を 2 回読むと、DB の値と戻り値がずれる
        clock.nowMillis = 1_700_000_000_000L
        clock.step = 1L

        val saved = repository.create("買い物")

        val memo = repository.getMemo(saved.id)!!
        assertEquals(SavedMemo(id = memo.id, updatedAt = memo.updatedAt), saved)
        assertEquals(1_700_000_000_000L, saved.updatedAt)
        assertEquals(memo.createdAt, memo.updatedAt)
    }

    /** C-U-19 */
    @Test
    fun update_returnsSavedMemo_withNow() = runTest {
        clock.nowMillis = 1_000L
        val id = repository.create("前").id
        clock.nowMillis = 5_000L
        clock.step = 1L

        val saved = repository.update(id, "後")

        assertEquals(SavedMemo(id = id, updatedAt = 5_000L), saved)
        assertEquals(5_000L, repository.getMemo(id)!!.updatedAt)
    }

    /** C-U-20 */
    @Test
    fun update_returnsNull_whenMemoMissing() = runTest {
        val saved = repository.update(999L, "ない")

        assertNull(saved)
        assertEquals(emptyList<MemoSummary>(), repository.observeSummaries().first())
    }

    /** C-U-21 */
    @Test
    fun delete_doesNotThrow_whenMissing() = runTest {
        repository.delete(999L)

        assertEquals(emptyList<MemoSummary>(), repository.observeSummaries().first())
    }

    /** C-U-22 DB の例外は握りつぶさずに呼び出し元へ届く（共通 詳細設計 13.2） */
    @Test
    fun errors_arePropagated() = runTest {
        val id = repository.create("ある").id
        db.close()

        assertThrows(Exception::class.java) { kotlinx.coroutines.runBlocking { repository.create("新") } }
        assertThrows(Exception::class.java) { kotlinx.coroutines.runBlocking { repository.update(id, "後") } }
        assertThrows(Exception::class.java) { kotlinx.coroutines.runBlocking { repository.delete(id) } }
    }
}
