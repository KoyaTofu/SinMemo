package com.example.sinmemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** アプリ全体の入口。Hilt の部品（DB・リポジトリなど）はここを起点に作られる。 */
@HiltAndroidApp
class SinMemoApplication : Application()
