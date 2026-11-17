package com.example.sinmemo.data.memo

import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/** [MemoRepository] の Room 版。日時は [clock] から取る（テストで固定できるように）。 */
class RoomMemoRepository @Inject constructor(
    private val dao: MemoDao,
    private val clock: Clock,
) : MemoRepository {

    override fun observeSummaries(): Flow<List<MemoSummary>> = dao.observeSummaries()

    override suspend fun getMemo(id: Long): Memo? = dao.getById(id)

    override suspend fun create(body: String): SavedMemo {
        // 現在時刻は 1 回だけ取り、DB に書く値と戻り値の両方に使う（共通 詳細設計 13.1）
        val now = clock.millis()
        val id = dao.insert(Memo(body = body, createdAt = now, updatedAt = now))
        return SavedMemo(id = id, updatedAt = now)
    }

    override suspend fun update(id: Long, body: String): SavedMemo? {
        val now = clock.millis()
        val changed = dao.updateBody(id = id, body = body, updatedAt = now)
        return if (changed == 0) null else SavedMemo(id = id, updatedAt = now)
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
