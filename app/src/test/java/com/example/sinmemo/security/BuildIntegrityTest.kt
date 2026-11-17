package com.example.sinmemo.security

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Properties

/**
 * セキュリティ テスト設計 A-15・C-2（ビルドの改ざん）の対策が外れていないことの確認。
 * Gradle 本体の ZIP は、ダウンロードしたあと gradle-wrapper.properties の distributionSha256Sum と照合される。
 * Gradle の版を上げたら、公式（services.gradle.org の .sha256）の値で両方を書き換える。
 */
class BuildIntegrityTest {

    private fun wrapperProperties(): Properties {
        // 単体テストは app フォルダで動くが、念のためプロジェクトの一番上で動いた場合も探す
        val file = listOf("../gradle/wrapper/gradle-wrapper.properties", "gradle/wrapper/gradle-wrapper.properties")
            .map(::File).firstOrNull { it.isFile }
            ?: error("gradle-wrapper.properties が見つからない（作業フォルダ: ${File("").absolutePath}）")
        return Properties().apply { file.inputStream().use(::load) }
    }

    @Test
    fun gradleDistribution_isPinnedByOfficialSha256() {
        val properties = wrapperProperties()

        assertEquals(
            "Gradle の版が変わった。公式の SHA-256 で distributionSha256Sum とこのテストを更新する",
            "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip",
            properties.getProperty("distributionUrl"),
        )
        assertEquals(OFFICIAL_SHA256, properties.getProperty("distributionSha256Sum"))
    }

    private companion object {
        const val GRADLE_VERSION = "9.9.1"

        /** services.gradle.org/distributions/gradle-9.9.1-bin.zip.sha256（2026-11-15 に ZIP を取得して照合） */
        const val OFFICIAL_SHA256 = "d8a18a7a841dd5df825d35a685fad6f2d7f12ac0c280e57b67c27b4a520ba228"
    }
}
