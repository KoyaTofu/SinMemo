package com.example.sinmemo.data.db

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element

/**
 * バックアップしないことの設定（共通 テスト設計 2.4 の C-U-28〜30・35、共通 詳細設計 13.5。開発者の決定 2026-11-17）。
 * メモと設定は、クラウドへのバックアップにも、機種変更（端末から端末への移行）にも出さない。
 * app/src/main の AndroidManifest.xml と res/xml/ の 2 つのファイルを XML として読む。
 */
class BackupRulesTest {

    private fun xml(path: String): Element {
        val file = listOf("src/main/$path", "app/src/main/$path").map(::File).firstOrNull { it.isFile }
            ?: error("$path が見つからない（作業フォルダ: ${File("").absolutePath}）")
        return DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.children(tag: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    /** exclude の domain（path はすべて "."） */
    private fun Element.excludedDomains(): Set<String> {
        val excludes = children("exclude")
        excludes.forEach { assertEquals("${it.getAttribute("domain")} の path", ".", it.getAttribute("path")) }
        return excludes.map { it.getAttribute("domain") }.toSet()
    }

    /** 外す場所（全部） */
    private val allDomains = setOf("root", "file", "database", "sharedpref", "external")

    /** C-U-28 Android 12 以降: クラウドにも機種変更にも、全部の場所を出さない。include はない */
    @Test
    fun dataExtraction_excludesEverything() {
        val root = xml("res/xml/data_extraction_rules.xml")
        for (section in listOf("cloud-backup", "device-transfer")) {
            val element = root.children(section).single()
            assertEquals(section, allDomains, element.excludedDomains())
            assertEquals("$section に include はない", 0, element.children("include").size)
        }
    }

    /** C-U-29 マニフェストでバックアップを止めている（android:allowBackup="false"）。設定ファイルの指定は残す */
    @Test
    fun manifest_disallowsBackup() {
        val application = xml("AndroidManifest.xml").children("application").single()
        val android = "http://schemas.android.com/apk/res/android"
        assertEquals("false", application.getAttributeNS(android, "allowBackup"))
        assertEquals("@xml/data_extraction_rules", application.getAttributeNS(android, "dataExtractionRules"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(android, "fullBackupContent"))
    }

    /** C-U-30 Android 11 以前: 全部の場所を外す。include はない */
    @Test
    fun fullBackup_excludesEverything() {
        val root = xml("res/xml/backup_rules.xml")
        assertEquals("full-backup-content", root.tagName)
        assertEquals(allDomains, root.excludedDomains())
        assertEquals("include はない", 0, root.children("include").size)
    }

    /** C-U-35 機種変更（Android 11 以前の requireFlags="deviceToDeviceTransfer"）でも含めるものがない */
    @Test
    fun fullBackup_hasNoDeviceToDeviceInclude() {
        val root = xml("res/xml/backup_rules.xml")
        val flagged = (0 until root.getElementsByTagName("*").length)
            .map { root.getElementsByTagName("*").item(it) as Element }
            .filter { it.hasAttribute("requireFlags") }
        assertEquals("requireFlags の付いた行はない", 0, flagged.size)
    }
}
