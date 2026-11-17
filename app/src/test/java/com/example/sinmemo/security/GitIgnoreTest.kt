package com.example.sinmemo.security

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** 秘密のファイル（.env・鍵）がリポジトリに入らないこと（リリース テスト設計 1.4 の RL-U-02、リリース 基本設計 6.7）。 */
class GitIgnoreTest {

    @Test
    fun secretFiles_areIgnored() {
        val file = listOf("../.gitignore", ".gitignore").map(::File).firstOrNull { it.isFile }
            ?: error(".gitignore が見つからない（作業フォルダ: ${File("").absolutePath}）")
        val lines = file.readLines().map { it.trim() }

        for (pattern in listOf(".env", "*.jks", "*.keystore", "*.p12", "*.pfx", "keystore.properties")) {
            assertTrue(".gitignore に $pattern の行がない", pattern in lines)
        }
    }
}
