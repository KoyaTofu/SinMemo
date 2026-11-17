package com.example.sinmemo.di

import com.example.sinmemo.data.memo.MemoRepository
import com.example.sinmemo.data.memo.RoomMemoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** interface と実装を結びつける。MemoRepository を求められたら RoomMemoRepository を渡す。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMemoRepository(impl: RoomMemoRepository): MemoRepository
}
