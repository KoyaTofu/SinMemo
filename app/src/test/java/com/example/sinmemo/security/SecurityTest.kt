package com.example.sinmemo.security

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.sinmemo.MainActivity
import com.example.sinmemo.R
import com.example.sinmemo.data.db.SinMemoDatabase
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.ui.navigation.MemoEditRoute
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/**
 * 脆弱性検証（docs/セキュリティ/テスト設計.md）で攻撃を再現したテスト。本物の MainActivity（Hilt・DB 込み）を使う。
 * 守れていることの確認（回帰テスト）と、今の弱点の記録（V-xx）を兼ねる。
 */
@RunWith(RobolectricTestRunner::class)
class SecurityTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var controller: ActivityController<MainActivity>? = null

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
    }

    /** 攻撃者が DB から読みたい「秘密のメモ」を 1 件入れ、その id を返す */
    private fun putSecretMemo(): Long {
        val db = Room.databaseBuilder(context, SinMemoDatabase::class.java, SinMemoDatabase.NAME).build()
        try {
            return runBlocking { db.memoDao().insert(Memo(body = SECRET, createdAt = 1, updatedAt = 1)) }
        } finally {
            db.close()
        }
    }

    /** Navigation が画面の id として使う値（navigation-common の RouteSerializer.generateHashCode と同じ計算） */
    private fun <T> KSerializer<T>.destinationId(): Int {
        var hash = descriptor.serialName.hashCode()
        for (i in 0 until descriptor.elementsCount) hash = 31 * hash + descriptor.getElementName(i).hashCode()
        return hash
    }

    /** 他のアプリが作れる Intent: 「明示的ディープリンク」の extras で、id のメモの編集画面を開かせる */
    private fun deepLinkToMemo(memoId: Long): Intent = launcherIntent()
        // 0 はグラフ（NavHost）全体の id、続けて編集画面の id
        .putExtra(DEEP_LINK_IDS, intArrayOf(0, serializer<MemoEditRoute>().destinationId()))
        .putParcelableArrayListExtra(DEEP_LINK_ARGS, arrayListOf(Bundle(), Bundle().apply { putLong("memoId", memoId) }))

    private fun launcherIntent() = Intent().setComponent(ComponentName(context, MainActivity::class.java))

    /** [callerPackage] から起動されたことにして MainActivity を開く */
    private fun launch(intent: Intent, callerPackage: String?): MainActivity {
        val c = Robolectric.buildActivity(MainActivity::class.java, intent)
        if (callerPackage != null) shadowOf(c.get()).setCallingPackage(callerPackage)
        controller = c.setup()
        // 設定（DataStore）を別のスレッドで読み終わるまで画面は空。何か描かれるまで待ってから判定する
        // （待たないと「攻撃が効かなかった」テストが、まだ何も出ていないだけで通ってしまう）
        composeRule.waitUntil(5_000) { isShowingList() || isShowingSecret() }
        composeRule.waitForIdle()
        return c.get()
    }

    private fun isShowingSecret(): Boolean =
        composeRule.onAllNodes(hasSetTextAction() and hasText(SECRET)).fetchSemanticsNodes().isNotEmpty()

    private fun isShowingList(): Boolean =
        composeRule.onAllNodes(hasContentDescription(context.getString(R.string.create_memo)))
            .fetchSemanticsNodes().isNotEmpty()

    // --- A-1 他のアプリから、アプリ内の画面を直接開かせる -------------------------------------------

    @Test
    fun control_deepLinkFromOwnApp_opensEditScreen() {
        // 比較用: 自分のアプリからなら開く（= 攻撃の Intent の作り方は正しい）
        launch(deepLinkToMemo(putSecretMemo()), callerPackage = context.packageName)

        assertTrue("自分のアプリからのディープリンクで編集画面が開かない（テストの前提が崩れている）", isShowingSecret())
    }

    @Test
    fun deepLinkFromOtherApp_isIgnored() {
        launch(deepLinkToMemo(putSecretMemo()), callerPackage = "com.attacker.app")

        assertFalse(isShowingSecret())
        assertTrue(isShowingList())
    }

    @Test
    fun deepLinkWithSpoofedReferrer_isIgnored() {
        // 呼び出し元を自分のアプリと偽る extras を付けても、Navigation は信用しない
        val intent = deepLinkToMemo(putSecretMemo())
            .putExtra(Intent.EXTRA_REFERRER_NAME, "android-app://${context.packageName}")

        launch(intent, callerPackage = context.packageName)

        assertFalse(isShowingSecret())
        assertTrue(isShowingList())
    }

    // --- A-2 壊れた extras でアプリを落とす ---------------------------------------------------

    @Test
    fun malformedExtras_doNotCrash() {
        val intent = launcherIntent()
            .putExtra(DEEP_LINK_IDS, intArrayOf(123456, -1))
            .putExtra(DEEP_LINK_ARGS, "not a list")
            .putExtra("android-support-nav:controller:deepLinkExtras", 42)

        launch(intent, callerPackage = context.packageName)

        assertTrue(isShowingList())
    }

    // --- A-11 タスクの乗っ取り（セキュリティ 基本設計 決定事項 3、テスト設計 C-1。対策済み） ------------------------------------------------

    @Test
    fun V03_mainActivity_hasEmptyTaskAffinity() {
        // android:taskAffinity="" を付けると、Android の中では null（どのタスクとも結び付かない）になる。
        // 付けていないと既定のパッケージ名になり、同じ taskAffinity を名乗った他のアプリの画面が入り込める
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)

        assertNotEquals(context.packageName, info.taskAffinity)
        assertTrue("taskAffinity が空でない: ${info.taskAffinity}", info.taskAffinity.isNullOrEmpty())
    }

    // --- 弱点の記録（セキュリティ 基本設計 決定事項 1・2）。対策したら期待値を逆にする -------------------------------------

    @Test
    fun weakness_V01_obscuredTouch_isAccepted() {
        // 他のアプリの画面が上に重なっている（FLAG_WINDOW_IS_OBSCURED）タップも受け付ける。
        // Android 12 以降は OS が止めるが、Android 10・11 ではアプリまで届く（タップジャッキング）
        val activity = launch(launcherIntent(), callerPackage = null)
        val fab = composeRule.onNode(hasContentDescription(context.getString(R.string.create_memo)))
            .fetchSemanticsNode().boundsInWindow.center

        // 時刻は今の時計から作る（古い時刻だと、ほかのテストのあとでタップとして扱われないことがある）
        val down = SystemClock.uptimeMillis()
        listOf(down to MotionEvent.ACTION_DOWN, down + 50 to MotionEvent.ACTION_UP).forEach { (time, action) ->
            val event = MotionEvent.obtain(
                down, time, action, 1,
                arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER }),
                arrayOf(MotionEvent.PointerCoords().apply { x = fab.x; y = fab.y; pressure = 1f; size = 1f }),
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, MotionEvent.FLAG_WINDOW_IS_OBSCURED,
            )
            activity.window.decorView.dispatchTouchEvent(event)
            event.recycle()
            composeRule.waitForIdle()
        }

        // 新規作成の画面（✓ がある）が開いた = 隠されたタップが通った
        assertTrue(
            composeRule.onAllNodes(hasContentDescription(context.getString(R.string.save)))
                .fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun weakness_V02_screenIsNotSecure() {
        // FLAG_SECURE がないので、スクリーンショット・画面録画・「最近使ったアプリ」のサムネイルに本文が写る
        val activity = launch(launcherIntent(), callerPackage = null)

        assertEquals(0, activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)
    }

    private companion object {
        const val SECRET = "口座の暗証番号 1234"
        const val DEEP_LINK_IDS = "android-support-nav:controller:deepLinkIds"
        const val DEEP_LINK_ARGS = "android-support-nav:controller:deepLinkArgs"
    }
}
