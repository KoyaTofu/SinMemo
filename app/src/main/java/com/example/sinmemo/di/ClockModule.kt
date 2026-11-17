package com.example.sinmemo.di

import com.example.sinmemo.data.time.SystemTimeChangeSource
import com.example.sinmemo.data.time.TimeChangeSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Singleton

/** 現在時刻の取り出し口。テストでは固定の Clock に差し替える。 */
@Module
@InstallIn(SingletonComponent::class)
object ClockModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = DeviceZoneClock
}

/** 時刻の変更の知らせ（共通 詳細設計 13.4）。テストでは偽物に差し替える。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeChangeModule {

    @Binds
    @Singleton
    abstract fun bindTimeChangeSource(impl: SystemTimeChangeSource): TimeChangeSource
}

/**
 * 端末の今のタイムゾーンを、使うたびに読み直す Clock。
 *
 * `Clock.systemDefaultZone()` は作った時点のタイムゾーンに固定されるため、アプリの起動中に端末のタイムゾーンが
 * 変わる（旅行先で自動設定が働くなど）と、再起動するまで古いタイムゾーンで日時を表示してしまう。
 * 4.2 のエミュレータ確認で、起動直後にタイムゾーンが変わり 5 時間ずれて表示されたことで見つかった。
 */
internal object DeviceZoneClock : Clock() {
    override fun getZone(): ZoneId = ZoneId.systemDefault()
    override fun withZone(zone: ZoneId): Clock = system(zone)
    override fun instant(): Instant = Instant.now()
    override fun millis(): Long = System.currentTimeMillis()
}
