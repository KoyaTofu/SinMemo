package com.example.sinmemo.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.sinmemo.data.settings.DataStoreSettingsRepository
import com.example.sinmemo.data.settings.SettingsRepository
import com.example.sinmemo.data.settings.SettingsResetFlag
import com.example.sinmemo.data.settings.createSettingsDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 設定の保存先（設定 詳細設計 3 章）。同じファイルに DataStore を 2 つ作ると例外になるので、アプリで 1 つだけ作る。
 * ファイルは files/datastore/settings.preferences_pb。壊れていたら初期値で上書きする（設定 詳細設計 10.2）。
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
        flag: SettingsResetFlag,
    ): DataStore<Preferences> = createSettingsDataStore(
        produceFile = { context.preferencesDataStoreFile(DataStoreSettingsRepository.FILE_NAME) },
        flag = flag,
    )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsRepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
}
