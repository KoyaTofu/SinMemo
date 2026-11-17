package com.example.sinmemo.data.time

import kotlinx.coroutines.flow.MutableSharedFlow

/** テスト用の時刻の変更の知らせ（共通 詳細設計 13.4）。[changes] に emit すると知らせが 1 つ流れる。 */
class FakeTimeChangeSource : TimeChangeSource {
    override val changes = MutableSharedFlow<Unit>()

    suspend fun notifyChanged() = changes.emit(Unit)
}
