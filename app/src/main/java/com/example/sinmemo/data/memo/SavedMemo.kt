package com.example.sinmemo.data.memo

/**
 * 保存したメモの id と、そのとき DB に書いた更新日時（エポックミリ秒）（共通 詳細設計 13.1）。
 * 一覧は id と更新日時の両方が合う行が出るまで待ってから、その行へ動かす（メモ一覧 詳細設計 9.3）。
 */
data class SavedMemo(val id: Long, val updatedAt: Long)
