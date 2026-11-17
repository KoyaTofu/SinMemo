package com.example.sinmemo.data.db

import com.example.sinmemo.BuildConfig
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 総合テストのスクリプト（scripts/seed_memos.ps1）が書く DB が、アプリの DB と合っていることの確認
 * （共通 テスト設計 2.4 の C-U-23〜27、共通 詳細設計 13.3）。合わないと、アプリが DB を開けずに落ちる。
 * スクリプトと、Room が出力するスキーマの JSON（一番大きい版）を文字列として読む。
 */
class SeedScriptConsistencyTest {

    /** 単体テストは app フォルダで動くが、念のためプロジェクトの一番上で動いた場合も探す */
    private fun find(path: String): File =
        listOf("../$path", path).map(::File).firstOrNull { it.exists() }
            ?: error("$path が見つからない（作業フォルダ: ${File("").absolutePath}）")

    private val script: String by lazy { find("scripts/seed_memos.ps1").readText(Charsets.UTF_8).removePrefix("﻿") }

    /** app/schemas の一番大きい版の JSON */
    private val schemaFile: File by lazy {
        find("app/schemas/com.example.sinmemo.data.db.SinMemoDatabase")
            .listFiles { f -> f.name.matches(Regex("""\d+\.json""")) }!!
            .maxBy { it.nameWithoutExtension.toInt() }
    }
    private val schema: String by lazy { schemaFile.readText(Charsets.UTF_8) }

    /** スクリプトの `$名前 = '値'` か `$名前 = 値` の値 */
    private fun scriptVariable(name: String): String =
        Regex("""^\$$name\s*=\s*'?([^'\r\n]*?)'?\s*$""", RegexOption.MULTILINE).find(script)?.groupValues?.get(1)
            ?: error("スクリプトに \$$name がない")

    /** JSON の `"名前": "値"` か `"名前": 数` の値（最初のもの） */
    private fun jsonValue(name: String): String =
        Regex(""""$name"\s*:\s*"?([^",\r\n]*)"?""").find(schema)?.groupValues?.get(1) ?: error("JSON に $name がない")

    /** JSON の createSql をすべて（${TABLE_NAME} を memos にして） */
    private fun schemaCreateSqls(): List<String> =
        Regex(""""createSql"\s*:\s*"((?:[^"\\]|\\.)*)"""").findAll(schema)
            .map { it.groupValues[1].replace("\\\"", "\"").replace("\${TABLE_NAME}", "memos") }.toList()

    /** スクリプトの AppendLine('CREATE ...;') の文（末尾の ; を外して） */
    private fun scriptCreateSqls(): List<String> =
        Regex("""AppendLine\('(CREATE [^']*?);?'\)""").findAll(script).map { it.groupValues[1] }.toList()

    /** C-U-23 */
    @Test
    fun identityHash_matchesLatestRoomSchema() {
        assertEquals(jsonValue("identityHash"), scriptVariable("IdentityHash"))
    }

    /** C-U-24 */
    @Test
    fun dbVersion_matchesLatestRoomSchema() {
        val version = jsonValue("version")
        assertEquals("JSON の版とファイル名の版", schemaFile.nameWithoutExtension, version)
        assertEquals(version, scriptVariable("DbVersion"))
    }

    /** C-U-25 */
    @Test
    fun dbName_matchesDatabaseName() {
        assertEquals(SinMemoDatabase.NAME, scriptVariable("DbName"))
    }

    /** C-U-26 */
    @Test
    fun createTableSql_matchesSchema() {
        val expected = schemaCreateSqls()
        assertTrue("JSON に createSql がない", expected.isNotEmpty())
        val actual = scriptCreateSqls()
        for (sql in expected) {
            assertTrue("スクリプトに次の文がない（アプリと DB の作りが違う）:\n$sql\nスクリプトの文:\n${actual.joinToString("\n")}", sql in actual)
        }
        // Room の型の印の表（setupQueries の 1 つ目）も同じ形で作る
        val masterTable = Regex(""""(CREATE TABLE IF NOT EXISTS room_master_table[^"]*)"""").find(schema)!!.groupValues[1]
        assertTrue("room_master_table の作り方が違う", masterTable in actual)
    }

    /** C-U-27 スクリプトが触るのはデバッグ版（単体テストはデバッグ版で動く） */
    @Test
    fun package_isDebugApplicationId() {
        assertEquals("com.example.sinmemo.debug", BuildConfig.APPLICATION_ID)
        assertEquals(BuildConfig.APPLICATION_ID, scriptVariable("Package"))
    }
}
