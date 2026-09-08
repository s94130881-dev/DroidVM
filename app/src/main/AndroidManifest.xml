@file:Suppress("UnstableApiUsage")

import java.net.URI
import java.security.MessageDigest

plugins {
alias(libs.plugins.android.application)
alias(libs.plugins.kotlin.compose)
}

fun runGit(vararg args: String): String {
require(rootDir.resolve(".git").exists()) {
"Not a git repository: $rootDir"
}

val proc = ProcessBuilder("git", *args)
    .directory(rootDir)
    .redirectErrorStream(false)
    .start()

val output = proc.inputStream
    .bufferedReader()
    .readText()
    .trim()

val exitCode = proc.waitFor()

require(exitCode == 0) {
    "git ${args.joinToString(" ")} failed with exit code $exitCode"
}

return output

}

fun runGitOrNull(vararg args: String): String? =
runCatching {
runGit(*args)
}.getOrNull()

val gitCommitCount =
runGit("rev-list", "--count", "HEAD").toInt()

val gitShortSha =
runGit("rev-parse", "--short", "HEAD")

val gitDescribe =
(runGitOrNull(
"describe",
"--long",
"--tags",
"--exclude=dev"
) ?: "0.0.0-$gitCommitCount-g$gitShortSha")
.removePrefix("v")
.removePrefix("V")

val generatedVersionName: String =
if (gitDescribe.matches(Regex(".-0-g[0-9a-f]+$"))) {
gitDescribe.replace(
Regex("-0-g[0-9a-f]+$"),
""
)
} else {
gitDescribe
.replace(
Regex("([^-]-g)"),
$$"r$1"
)
.replace("-", ".")
}

val generatedVersionCode =
gitCommitCount * 10

println("Version name: $generatedVersionName")
println("Version code: $generatedVersionCode")

android {

namespace = "cn.classfun.droidvm"

compileSdk {
    version = release(37)
}

defaultConfig {

    applicationId = "cn.classfun.droidvm"

    minSdk = 33
    targetSdk = 37

    versionCode = generatedVersionCode
    versionName = generatedVersionName

    testInstrumentationRunner =
        "androidx.test.runner.AndroidJUnitRunner"

    ndk {
        abiFilters += listOf(
            "arm64-v8a",
            "x86_64"
        )
    }

    externalNativeBuild {
        cmake {

            cppFlags += "-std=c++20"

            arguments +=
                "-DANDROID_STL=c++_static"

            arguments +=
                "-DDROIDVM_VERSION=${versionName}"
        }
    }
}

externalNativeBuild {

    cmake {

        path = file(
            "src/main/cpp/CMakeLists.txt"
        )

        version = "3.22.1"
    }
}

buildTypes {

    release {

        isMinifyEnabled = false

        proguardFiles(
            getDefaultProguardFile(
                "proguard-android-optimize.txt"
            ),
            "proguard-rules.pro"
        )
    }
}

compileOptions {

    sourceCompatibility =
        JavaVersion.VERSION_11

    targetCompatibility =
        JavaVersion.VERSION_11
}

kotlin {

    compilerOptions {

        jvmTarget =
            org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

buildFeatures {

    aidl = true

    buildConfig = true

    compose = true
}

sourceSets {

    getByName("main") {

        kotlin.srcDir(
            "src/main/vendor"
        )
    }
}

testOptions {

    unitTests {

        isReturnDefaultValues = true
    }
}

packaging {

    jniLibs {

        useLegacyPackaging = true
    }
}

androidResources {

    ignoreAssetsPatterns += listOf(
        "*-comptime.zip",
        "*.7z"
    )
}

}

/*

* ================================================================
* Native binary assets
* ================================================================
  */

abstract class CopyNativeBinAssetsTask :
DefaultTask() {

@get:InputDirectory
abstract val cmakeOutputDir:
    DirectoryProperty

@get:OutputDirectory
abstract val outputDir:
    DirectoryProperty

@TaskAction
fun copy() {

    val outDir =
        outputDir.get().asFile

    outDir.deleteRecursively()

    val cmakeDir =
        cmakeOutputDir.get().asFile

    if (!cmakeDir.exists()) return

    val binaries =
        setOf(
            "droidvm",
            "daemon"
        )

    cmakeDir
        .walkTopDown()
        .filter {
            it.name in binaries &&
                it.isFile
        }
        .forEach { src ->

            val abi =
                src.parentFile.name

            val dest =
                File(
                    outDir,
                    "bin/$abi/${src.name}"
                )

            dest.parentFile.mkdirs()

            src.copyTo(
                dest,
                overwrite = true
            )
        }
}

}

/*

* ================================================================
* Prebuilt JNI libraries
* ================================================================
  */

abstract class UnpackComptimeJniLibsTask :
DefaultTask() {

@get:Inject
abstract val archives:
    ArchiveOperations

@get:Inject
abstract val fs:
    FileSystemOperations

@get:InputDirectory
abstract val prebuiltsDir:
    DirectoryProperty

@get:OutputDirectory
abstract val outputDir:
    DirectoryProperty

@TaskAction
fun unpack() {

    val outDir =
        outputDir.get().asFile

    outDir.deleteRecursively()

    val prebuilts =
        prebuiltsDir.get().asFile

    val re =
        Regex(
            "^prebuilt-(.+)-comptime\\.zip$"
        )

    val zips =
        prebuilts.listFiles {
                f ->
            f.isFile &&
                re.matches(f.name)
        } ?: return

    for (zip in zips) {

        val abi =
            re.find(zip.name)!!
                .groupValues[1]

        fs.copy {

            from(
                archives.zipTree(zip)
            )

            into(
                File(
                    outDir,
                    abi
                )
            )
        }
    }
}

}

/*

* ================================================================
* Regenerate prebuilts
* ================================================================
  */

abstract class RegenPrebuiltsTask :
DefaultTask() {

@get:Inject
abstract val exec:
    ExecOperations

@get:Internal
abstract val prebuiltRoot:
    DirectoryProperty

@get:Internal
abstract val prebuiltsOut:
    DirectoryProperty

@TaskAction
fun regen() {

    val root =
        prebuiltRoot.get().asFile

    val out =
        prebuiltsOut.get().asFile

    logger.lifecycle(
        "DroidVM-Prebuilt-Root detected; regenerating prebuilts"
    )

    exec.exec {

        workingDir = root

        commandLine(
            "python3",
            "auto-build.py",
            "--out",
            out.absolutePath
        )
    }
}

}

/*

* ================================================================
* Terminal font
* ================================================================
  */

abstract class FetchTerminalFontTask :
DefaultTask() {

@get:Inject
abstract val archives:
    ArchiveOperations

@get:Input
abstract val url:
    Property<String>

@get:Input
abstract val sha256:
    Property<String>

@get:OutputDirectory
abstract val outputDir:
    DirectoryProperty

@TaskAction
fun fetch() {

    val ttf =
        File(
            outputDir.get().asFile,
            "fonts/MapleMonoNL-NF-Regular.ttf"
        )

    if (
        ttf.isFile &&
        sha256Hex(ttf) == sha256.get()
    ) {
        return
    }

    ttf.parentFile.mkdirs()

    val tmpZip =
        File(
            temporaryDir,
            "font.zip"
        )

    try {

        URI(url.get())
            .toURL()
            .openStream()
            .use { input ->

                tmpZip.outputStream()
                    .use {
                        input.copyTo(it)
                    }
            }

    } catch (e: Exception) {

        logger.warn(
            "Could not download terminal font: ${e.message}"
        )

        return
    }

    val src =
        archives
            .zipTree(tmpZip)
            .matching {
                include(
                    "**/*NL-NF-Regular.ttf"
                )
            }
            .files
            .firstOrNull()

    if (src == null) {

        logger.warn(
            "Terminal font not found"
        )

        return
    }

    src.copyTo(
        ttf,
        overwrite = true
    )

    if (
        sha256Hex(ttf) != sha256.get()
    ) {

        logger.warn(
            "Terminal font SHA-256 mismatch"
        )
    }
}

private fun sha256Hex(
    file: File
): String {

    val md =
        MessageDigest.getInstance(
            "SHA-256"
        )

    file.inputStream().use { ins ->

        val buf =
            ByteArray(8192)

        var n =
            ins.read(buf)

        while (n >= 0) {

            md.update(
                buf,
                0,
                n
            )

            n =
                ins.read(buf)
        }
    }

    return md.digest()
        .joinToString("") {
            "%02x".format(
                it.toInt() and 0xFF
            )
        }
}

}

/*

* ================================================================
* Project directories
* ================================================================
  */

val prebuiltRootDir =
rootProject.layout.projectDirectory
.dir("DroidVM-Prebuilt-Root")

val prebuiltsSubmoduleDir =
rootProject.layout.projectDirectory
.dir(
"app/src/main/assets/prebuilts"
)

val regenPrebuilts =
tasks.register<RegenPrebuiltsTask>(
"regenPrebuilts"
) {

    description =
        "Regenerate DroidVM prebuilts"

    prebuiltRoot.set(
        prebuiltRootDir
    )

    prebuiltsOut.set(
        prebuiltsSubmoduleDir
    )

    onlyIf {

        val autoBuild =
            prebuiltRootDir
                .dir("auto-build")
                .asFile

        prebuiltRootDir
            .file("auto-build.py")
            .asFile
            .isFile &&
            autoBuild.isDirectory &&
            (
                autoBuild
                    .listFiles()
                    ?.any {
                        it.name != ".gitignore"
                    } == true
            )
    }
}

tasks.named("preBuild")
.configure {
dependsOn(regenPrebuilts)
}

/*

* ================================================================
* Variant generated tasks
* ================================================================
  */

androidComponents {

onVariants { variant ->

    val variantName =
        variant.name
            .replaceFirstChar {
                it.uppercase()
            }

    val unpackComptimeTask =
        tasks.register<UnpackComptimeJniLibsTask>(
            "unpackComptimeJniLibs$variantName"
        ) {

            description =
                "Unpack native JNI libraries"

            dependsOn(
                regenPrebuilts
            )

            prebuiltsDir.set(
                prebuiltsSubmoduleDir
            )

            outputDir.set(
                layout.buildDirectory.dir(
                    "generated/comptime_jnilibs/${variant.name}"
                )
            )
        }

    variant.sources
        .jniLibs
        ?.addGeneratedSourceDirectory(
            unpackComptimeTask,
            UnpackComptimeJniLibsTask::outputDir
        )

    val fetchFontTask =
        tasks.register<FetchTerminalFontTask>(
            "fetchTerminalFont$variantName"
        ) {

            url.set(
                "https://github.com/subframe7536/maple-font/releases/download/v7.9/MapleMonoNL-NF.zip"
            )

            sha256.set(
                "aa3b096bc92df8503d77482b285a0567bafa6e83230d969700f455e610b1f655"
            )

            outputDir.set(
                layout.buildDirectory.dir(
                    "generated/font_assets/${variant.name}"
                )
            )
        }

    variant.sources
        .assets
        ?.addGeneratedSourceDirectory(
            fetchFontTask,
            FetchTerminalFontTask::outputDir
        )

    val copyNativeTask =
        tasks.register<CopyNativeBinAssetsTask>(
            "copyNativeBinAssets$variantName"
        ) {

            dependsOn(
                "externalNativeBuild$variantName"
            )

            cmakeOutputDir.set(
                layout.buildDirectory.dir(
                    "intermediates/cmake/${variant.name}/obj"
                )
            )

            outputDir.set(
                layout.buildDirectory.dir(
                    "generated/droidvm_assets/${variant.name}"
                )
            )
        }

    variant.sources
        .assets
        ?.addGeneratedSourceDirectory(
            copyNativeTask,
            CopyNativeBinAssetsTask::outputDir
        )
}

}

/*

* ================================================================
* Dependencies
* ================================================================
  */

dependencies {

/*
 * ------------------------------------------------------------
 * Shizuku
 * ------------------------------------------------------------
 *
 * API:
 *   rikka.shizuku.Shizuku
 *
 * Provider:
 *   rikka.shizuku.ShizukuProvider
 *
 * API 13+ supports UserService.
 */

implementation(
    "dev.rikka.shizuku:api:13.1.5"
)

implementation(
    "dev.rikka.shizuku:provider:13.1.5"
)

implementation(
    "androidx.annotation:annotation:1.8.2"
)

/*
 * ------------------------------------------------------------
 * Existing DroidVM dependencies
 * ------------------------------------------------------------
 */

implementation(libs.activity)

implementation(libs.annotation.jvm)

implementation(libs.appcompat)

implementation(
    libs.auto.service.annotations
)

implementation(
    platform(libs.compose.bom)
)

implementation(
    libs.compose.animation
)

implementation(
    libs.compose.foundation
)

implementation(
    libs.compose.ui
)

implementation(
    libs.compose.material3
)

implementation(
    libs.constraintlayout
)

implementation(
    libs.libsu.core
)

implementation(
    libs.libsu.nio
)

implementation(
    libs.libsu.service
)

implementation(
    libs.markdown.parser
)

implementation(
    libs.kotlinx.collections.immutable
)

implementation(
    libs.material
)

implementation(
    libs.okhttp3
)

implementation(
    libs.snakeyaml
)

implementation(
    libs.xz
)

implementation(
    libs.zstd
) {
    artifact {
        type = "aar"
    }
}

testImplementation(
    libs.zstd
)

implementation(
    libs.termux.emulator
)

implementation(
    libs.termux.view
)

testImplementation(
    libs.junit
)

annotationProcessor(
    libs.auto.service
)

androidTestImplementation(
    libs.espresso.core
)

androidTestImplementation(
    libs.ext.junit
)

}
