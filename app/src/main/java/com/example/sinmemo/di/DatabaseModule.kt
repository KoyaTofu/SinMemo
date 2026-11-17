package com.example.sinmemo.di

import android.content.Context
import androidx.room.Room
import com.example.sinmemo.data.db.SinMemoDatabase
import com.example.sinmemo.data.memo.MemoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SinMemoDatabase =
        Room.databaseBuilder(context, SinMemoDatabase::class.java, SinMemoDatabase.NAME).build()

    @Provides
    fun provideMemoDao(database: SinMemoDatabase): MemoDao = database.memoDao()
}
