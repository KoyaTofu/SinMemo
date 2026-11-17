package com.example.sinmemo.data.time

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.time.ZoneId
import java.util.TimeZone
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** SystemTimeChangeSource（共通 テスト設計 2.4 の C-U-31〜34、共通 詳細設計 13.4）。 */
@RunWith(RobolectricTestRunner::class)
class SystemTimeChangeSourceTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val source = SystemTimeChangeSource(app)
    private val received = mutableListOf<Unit>()

    private fun TestScope.collect() =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { source.changes.collect { received += it } }

    private fun send(action: String) {
        app.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** この部品の受け手（タイムゾーンの知らせを受けるもの）の数 */
    private fun ourReceivers() =
        shadowOf(app).registeredReceivers.count { it.intentFilter.hasAction(Intent.ACTION_TIMEZONE_CHANGED) }

    /** C-U-31 */
    @Test
    fun emits_onTimezoneTimeAndDateChanged() = runTest {
        collect()

        send(Intent.ACTION_TIMEZONE_CHANGED)
        assertEquals(1, received.size)
        send(Intent.ACTION_TIME_CHANGED)
        assertEquals(2, received.size)
        send(Intent.ACTION_DATE_CHANGED)
        assertEquals(3, received.size)
    }

    /** C-U-32 */
    @Test
    fun ignoresOtherBroadcasts() = runTest {
        collect()

        send(Intent.ACTION_SCREEN_ON)

        assertEquals(0, received.size)
    }

    /** C-U-33 */
    @Test
    fun unregisters_whenCollectionStops() = runTest {
        assertEquals(0, ourReceivers())
        val job = collect()
        assertEquals(1, ourReceivers())

        job.cancel()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, ourReceivers())
    }

    /** C-U-34 タイムゾーンの知らせのあと、ZoneId.systemDefault() が新しいタイムゾーンを返す */
    @Test
    fun timezoneChanged_resetsDefaultZone() = runTest {
        val originalDefault = TimeZone.getDefault()
        val originalProperty = System.getProperty("user.timezone")
        try {
            // 端末のタイムゾーンは東京に変わったが、Java はまだ古い UTC を覚えている
            System.setProperty("user.timezone", "Asia/Tokyo")
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals(ZoneId.of("UTC"), ZoneId.systemDefault())
            collect()

            send(Intent.ACTION_TIMEZONE_CHANGED)

            assertEquals(1, received.size)
            assertEquals(ZoneId.of("Asia/Tokyo"), ZoneId.systemDefault())
        } finally {
            if (originalProperty == null) System.clearProperty("user.timezone") else System.setProperty("user.timezone", originalProperty)
            TimeZone.setDefault(originalDefault)
        }
    }

    /** C-U-36 ほかのアプリからの偽の知らせを受けないよう、RECEIVER_NOT_EXPORTED で登録している */
    @Test
    fun registers_notExported() = runTest {
        collect()

        val flags = shadowOf(app).registeredReceivers
            .single { it.intentFilter.hasAction(Intent.ACTION_TIMEZONE_CHANGED) }.flags
        assertTrue("RECEIVER_NOT_EXPORTED がない（flags=$flags）", flags and Context.RECEIVER_NOT_EXPORTED != 0)
        assertEquals("RECEIVER_EXPORTED が付いている（flags=$flags）", 0, flags and Context.RECEIVER_EXPORTED)
    }
}
