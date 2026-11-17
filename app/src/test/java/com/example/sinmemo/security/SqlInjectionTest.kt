package com.example.sinmemo.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.data.db.SinMemoDatabase
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.data.memo.MemoDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A-6 SQL インジェクション（セキュリティ テスト設計 A-6）。本文に SQL を混ぜても、命令として実行されず文字として保存される。
 * Room は引数を「?」に結びつける（バインド）ので、文字列をつなげて SQL を作る所がない。
 */
@RunWith(RobolectricTestRunner::class)
class SqlInjectionTest {

    private lateinit var db: SinMemoDatabase
    private lateinit var dao: MemoDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SinMemoDatabase::class.java).allowMainThreadQueries().build()
        dao = db.memoDao()
    }

    @After
    fun tearDown() = db.close()

    private val payloads = listOf(
        "'); DROP TABLE memos; --",
        "x' WHERE 1=1; DELETE FROM memos; --",
        "\" OR \"1\"=\"1",
        "%_\\' || (SELECT sqlite_version()) || '",
    )

    @Test
    fun sqlInBody_isStoredAsText_andTableSurvives() = runTest {
        val keep = dao.insert(Memo(body = "残っているべきメモ", createdAt = 1, updatedAt = 1))
        val ids = payloads.map { dao.insert(Memo(body = it, createdAt = 2, updatedAt = 2)) }

        payloads.zip(ids).forEach { (payload, id) -> assertEquals(payload, dao.getById(id)?.body) }
        assertEquals("残っているべきメモ", dao.getById(keep)?.body)
        assertEquals(payloads.size + 1, dao.observeSummaries().first().size)
    }

    @Test
    fun sqlInUpdate_changesOnlyThatMemo() = runTest {
        val a = dao.insert(Memo(body = "A", createdAt = 1, updatedAt = 1))
        val b = dao.insert(Memo(body = "B", createdAt = 1, updatedAt = 1))

        dao.updateBody(a, "x', body = 'hacked", updatedAt = 3)

        assertEquals("x', body = 'hacked", dao.getById(a)?.body)
        assertEquals("B", dao.getById(b)?.body)
    }
}
