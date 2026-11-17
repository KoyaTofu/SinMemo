package com.example.sinmemo.data.time

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** 端末のタイムゾーン・日付・時刻の変更の知らせ（共通 詳細設計 13.4）。 */
interface TimeChangeSource {
    /** 端末のタイムゾーン・日付・時刻が変わるたびに 1 つ流れる */
    val changes: Flow<Unit>
}

/**
 * 端末の知らせ（ブロードキャスト）を Flow にする。購読している間だけ受け手を登録する。
 * ほかのアプリからの偽の知らせは受けない（RECEIVER_NOT_EXPORTED。システムの知らせは届く）。
 */
class SystemTimeChangeSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : TimeChangeSource {

    override val changes: Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action !in ACTIONS) return
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    // Java が覚えている「今のタイムゾーン」を捨てさせ、ZoneId.systemDefault() が新しい値を返すようにする。
                    // Android もプロセスに同じことをするが、この受け手との順番は決まっていないため
                    TimeZone.setDefault(null)
                }
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply { ACTIONS.forEach(::addAction) }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private companion object {
        /** タイムゾーン、時刻を手で変えた（TIME_SET）、日付が変わった（0 時を過ぎて年が変わったときにも） */
        val ACTIONS = listOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_DATE_CHANGED)
    }
}
