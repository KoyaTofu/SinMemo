package com.example.sinmemo.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.sinmemo.data.memo.Memo
import com.example.sinmemo.data.memo.MemoDao

/**
 * アプリの DB（ファイル名 [NAME]）。
 * テーブルを変えるときは version を上げ、app/schemas/ の JSON をもとにマイグレーションを書く。
 */
@Database(entities = [Memo::class], version = 1, exportSchema = true)
abstract class SinMemoDatabase : RoomDatabase() {
    abstract fun memoDao(): MemoDao

    companion object {
        const val NAME = "sinmemo.db"
    }
}
