package com.example.sinmemo

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.core.splashscreen.R as SplashR

/** ランチャーアイコンとスプラッシュのアイコンの指定（アイコン 詳細設計 1〜4 章） */
@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun manifest_usesLauncherIcon() {
        assertEquals(R.mipmap.ic_launcher, context.applicationInfo.icon)
    }

    @Test
    @Config(sdk = [33])
    fun launcherIcon_isAdaptive_withDesignedLayers() {
        // アイコンを読み、3 つの層が入っていることを確かめる（単色版は Android 13 以降で読まれる）
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)!!

        assertTrue("adaptive icon でない: ${icon::class.java}", icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        assertEquals(0xFFF3F4F7.toInt(), (icon.background as ColorDrawable).color)
        assertNotNull(icon.foreground)
        assertNotNull("単色版がない", icon.monochrome)
    }

    @Test
    fun vectorDrawables_canBeInflated() {
        // pathData（だ円の弧を含む）が読めること
        listOf(R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome).forEach { id ->
            val drawable = ContextCompat.getDrawable(context, id)
            assertNotNull(drawable)
            assertEquals(108f, drawable!!.intrinsicWidth / context.resources.displayMetrics.density, 0.5f)
        }
    }

    @Test
    fun splashTheme_showsForegroundIcon() {
        val theme = context.resources.newTheme().apply { applyStyle(R.style.Theme_SinMemo_Starting, true) }
        val value = TypedValue()

        assertTrue(theme.resolveAttribute(SplashR.attr.windowSplashScreenAnimatedIcon, value, true))
        assertEquals(R.drawable.ic_launcher_foreground, value.resourceId)
    }
}
