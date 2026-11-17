package com.example.sinmemo.data

import kotlin.math.min

/**
 * 一時的な読み込みの失敗のあと、読み直すまでの待ち時間（ミリ秒）。[attempt] は 0 から数えた読み直しの回数。
 * 0.5 秒、1 秒、1.5 秒…最大 5 秒（設定 詳細設計 10.3、メモ一覧 詳細設計 9.4）。
 */
fun retryDelayMillis(attempt: Long): Long = min(500L * (attempt + 1), 5_000L)
