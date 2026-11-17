import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// ---- リリース版の署名（docs/リリース/基本設計.md 2 章・6 章、詳細設計.md 2 章・3 章） ----
// 鍵の場所とパスワードは、項目ごとに「環境変数 → リポジトリ直下の .env」の順に探す。値はリポジトリに書かない。
// .env を読むのはビルドの設定の段階（署名の設定に値が要るため）。確かめて警告・エラーを出すのは、
// リリース版を本当に組み立てるとき（checkReleaseSigning。詳細設計 3.2）。

/** 署名に使う項目（.env.example にも同じ名前を書く） */
val signingKeyNames = listOf(
    "SINMEMO_KEYSTORE_PATH",
    "SINMEMO_KEYSTORE_PASSWORD",
    "SINMEMO_KEY_ALIAS",
    "SINMEMO_KEY_PASSWORD",
)

/** .env を読んだ結果（詳細設計 3.3）。文にはパスワードなどの中身を入れない */
data class DotEnv(
    /** 読めた項目 */
    val values: Map<String, String> = emptyMap(),
    /** 「=」のない行の行番号（1 から） */
    val linesWithoutEquals: List<Int> = emptyList(),
    /** SINMEMO_ で始まるが signingKeyNames にない項目名 */
    val unknownNames: List<String> = emptyList(),
    /** 項目名の書き方の警告（小文字・SINMEMO_ で始まらない・同じ名前が 2 回）。行番号だけを入れ、値は入れない */
    val nameWarnings: List<String> = emptyList(),
    /** 文字コードのエラーの文（基本設計 6.3）。なければ null */
    val error: String? = null,
)

/**
 * .env を読む（基本設計 2.3・6.3）。UTF-8 だけを読み、先頭の BOM は外す。UTF-16（BOM あり・なし）や
 * UTF-8 として正しくないバイトの並び（Shift_JIS など）は error に理由を入れて返す。
 * 1 行に「項目名=値」。行の頭の「export 」は外す。最初の = で分け、前後の空白を取り、値を囲む " か ' を外す。
 * # で始まる行と空の行は読まない。行の途中の #、\、$ はそのまま。
 */
fun readDotEnv(file: File): DotEnv {
    if (!file.isFile) return DotEnv()
    var bytes = file.readBytes()
    val utf16 = ".env の文字コードが UTF-16 です。UTF-8 で保存し直してください" +
        "（メモ帳の「名前を付けて保存」で文字コードを「UTF-8」にする）: ${file.absolutePath}"
    val notUtf8 = ".env が UTF-8 として読めません（Shift_JIS などで保存されているようです）。UTF-8 で保存し直してください" +
        "（メモ帳の「名前を付けて保存」で文字コードを「UTF-8」にする）: ${file.absolutePath}"
    fun startsWith(vararg prefix: Int) =
        bytes.size >= prefix.size && prefix.indices.all { bytes[it] == prefix[it].toByte() }
    if (startsWith(0xFF, 0xFE) || startsWith(0xFE, 0xFF)) return DotEnv(error = utf16)
    if (startsWith(0xEF, 0xBB, 0xBF)) bytes = bytes.copyOfRange(3, bytes.size)
    // BOM なしの UTF-16 は、ASCII の文字の半分が 0 のバイトになる
    if (bytes.any { it == 0.toByte() }) return DotEnv(error = utf16)
    val text = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        return DotEnv(error = notUtf8)
    }
    val values = linkedMapOf<String, String>()
    val linesWithoutEquals = mutableListOf<Int>()
    val nameWarnings = mutableListOf<String>()
    val firstLineOf = mutableMapOf<String, Int>()
    text.lines().forEachIndexed { index, raw ->
        // シェルの書き方（export SINMEMO_...=...）も読めるように、行の頭の export は外す
        val line = raw.trim().replaceFirst(Regex("""^export\s+"""), "")
        if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
        if ("=" !in line) {
            linesWithoutEquals += index + 1
            return@forEachIndexed
        }
        val name = line.substringBefore("=").trim()
        val lineNo = index + 1
        // 名前の書き方の誤りを知らせる。どの文にも値は入れない（基本設計 6.4）
        when {
            name != name.uppercase() ->
                nameWarnings += "警告: .env の $lineNo 行目の項目名に小文字があります（項目名は大文字で書きます。docs/リリース/基本設計.md 2.1）"
            !name.startsWith("SINMEMO_") ->
                nameWarnings += "警告: .env の $lineNo 行目の項目名が SINMEMO_ で始まらないため使いません（docs/リリース/基本設計.md 2.1）"
        }
        firstLineOf[name]?.let { first ->
            nameWarnings += "警告: .env の $lineNo 行目は $first 行目と同じ項目名です。後の行の値を使います"
        } ?: run { firstLineOf[name] = lineNo }
        var value = line.substringAfter("=").trim()
        if (value.length >= 2 && value.first() == value.last() && value.first() in "\"'") {
            value = value.substring(1, value.length - 1)
        }
        values[name] = value
    }
    val unknownNames = values.keys.filter { it.startsWith("SINMEMO_") && it !in signingKeyNames }
    return DotEnv(values, linesWithoutEquals, unknownNames, nameWarnings)
}

/** 実行するタスクの名前がすべて Debug を含む（デバッグ版だけを作る）なら、.env を読まない（基本設計 6.2） */
val onlyDebugRequested = gradle.startParameter.taskNames.let { it.isNotEmpty() && it.all { name -> "Debug" in name } }

val dotEnvFile: File = rootProject.file(".env")
val dotEnv: DotEnv = if (onlyDebugRequested) DotEnv() else readDotEnv(dotEnvFile)

/** 見つかった項目（空の値はないものとする）。.env にエラーがあるときは環境変数だけを使う */
val signingValues: Map<String, String> = signingKeyNames.mapNotNull { name ->
    val fromDotEnv = if (dotEnv.error == null) dotEnv.values[name] else null
    val value = System.getenv(name)?.takeIf { it.isNotEmpty() } ?: fromDotEnv?.takeIf { it.isNotEmpty() }
    value?.let { name to it }
}.toMap()

/** 4 項目がそろったときの鍵のファイル。そろわなければ null（未署名でビルドする。基本設計 2.4） */
val releaseKeystore: File? = if (signingValues.size == signingKeyNames.size) {
    rootProject.file(signingValues.getValue("SINMEMO_KEYSTORE_PATH"))
} else {
    null
}

/**
 * リリース版の組み立ての最初に、署名の情報を確かめる（詳細設計 3.2）。
 * 出す文はここ（設定の段階）でまとめ、パスワードの値は入れない（項目名・行番号・鍵のファイルの場所だけ）。
 */
val checkReleaseSigning = tasks.register("checkReleaseSigning") {
    group = "verification"
    description = "リリース版の署名の情報（.env・環境変数）を確かめる"
    val warnings = mutableListOf<String>()
    var error: String? = dotEnv.error
    if (error == null) {
        dotEnv.linesWithoutEquals.forEach { warnings += "警告: .env の $it 行目に「=」がないため読み飛ばしました" }
        warnings += dotEnv.nameWarnings
        dotEnv.unknownNames.forEach {
            warnings += "警告: .env に知らない項目があります: $it（書き間違いではありませんか。docs/リリース/基本設計.md 2.1）"
        }
        val keystore = releaseKeystore
        if (keystore == null) {
            val missing = signingKeyNames.filterNot { it in signingValues }
            warnings += "警告: 署名の情報がないため、リリース版を未署名でビルドします（docs/リリース/基本設計.md 2.4）。" +
                "足りない項目: ${missing.joinToString(", ")}"
        } else if (!keystore.isFile) {
            error = "署名の鍵のファイルが見つかりません: $keystore（SINMEMO_KEYSTORE_PATH を確かめてください）"
        }
    }
    val message = error
    doLast {
        warnings.forEach { logger.warn(it) }
        if (message != null) throw GradleException(message)
    }
}

android {
    namespace = "com.example.sinmemo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.sinmemo"
        minSdk = 29
        targetSdk = 37
        // 公開は 1.0.0 を始点にする（docs/リリース/基本設計.md 3 章・6.5。7.4 の 1.0.1 から戻した）
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = signingValues.getValue("SINMEMO_KEYSTORE_PASSWORD")
                keyAlias = signingValues.getValue("SINMEMO_KEY_ALIAS")
                keyPassword = signingValues.getValue("SINMEMO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // デバッグ版を別のアプリにして、リリース版と並べて入れられるようにする（docs/リリース/基本設計.md 6.1）。
            // ホーム画面の名前は src/debug/res/values/strings.xml の launcher_name
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 4 項目がそろったときだけ署名する。なければ未署名（app-release-unsigned.apk）
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // 設定画面のバージョン表示に BuildConfig.VERSION_NAME を使う（設定 詳細設計 決定事項 1）
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Robolectric で Android のリソースを使えるようにする
            isIncludeAndroidResources = true
            // 素の JUnit のテストで android.util.Log などを呼んでも落ちないようにする（ViewModel の失敗のログ）
            isReturnDefaultValues = true
            all {
                // Robolectric が SDK 36 の環境を作るときに JDK の内部 API を使うため（使わないと
                // IllegalAccessException: ... does not export jdk.internal.access で全テストが失敗する）
                it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}

// リリース版の変種を組み立てるとき（assembleRelease、assemble、build、Android Studio の「Generate APKs」など）に
// 必ず最初に動く pre〈名前〉Build に checkReleaseSigning をつなぐ。タスクの名前の付け方に頼らない（詳細設計 3.2）
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val pre = "pre${variant.name.replaceFirstChar { it.uppercase() }}Build"
        tasks.matching { it.name == pre }.configureEach { dependsOn(checkReleaseSigning) }
    }
}

room {
    // DB の構造（スキーマ）を JSON で出力し、コミットする（共通 詳細設計 4.1）
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    // Compose の UI テストを Robolectric で PC 上で動かす（共通 詳細設計 11 章）
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // ui-test-junit4 が連れてくる espresso-core 3.5.0 は SDK 36 で動かない
    // （InputManager.getInstance() がない）ため、新しい版にそろえる
    testImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
