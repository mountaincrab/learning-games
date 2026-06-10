import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

// --- Versioning derived from git ---------------------------------------------
// Git tags are the single source of truth. versionName/versionCode are computed
// at build time so no version-bump commit is ever needed — see CLAUDE.md.
fun git(vararg args: String): String? = try {
    val out = providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }
    if (out.result.get().exitValue == 0)
        out.standardOutput.asText.get().trim().ifEmpty { null }
    else null
} catch (_: Exception) { null }   // git missing / not a repo

// Latest "v1.2.3" tag -> Triple(1,2,3); falls back to 0.0.0 when no tags exist.
fun latestSemverTag(): Triple<Int, Int, Int> {
    val tag = git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*")
        ?.removePrefix("v") ?: "0.0.0"
    val p = tag.split(".").mapNotNull { it.toIntOrNull() }
    return Triple(p.getOrElse(0) { 0 }, p.getOrElse(1) { 0 }, p.getOrElse(2) { 0 })
}

// Monotonic build number = total commit count (always-increasing positive int).
fun gitCommitCount(): Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

// VERSION_BRANCH/VERSION_SHA let CI inject the true branch + commit. On a
// pull_request the reserved GITHUB_REF_NAME/GITHUB_SHA hold the merge ref
// ("<pr>/merge") and the ephemeral merge commit, and GitHub forbids overriding
// the GITHUB_* vars — so the workflow sets these unreserved names instead.
fun currentBranch(): String =
    System.getenv("VERSION_BRANCH")?.takeIf { it.isNotBlank() }
        ?: System.getenv("GITHUB_REF_NAME")
        ?: git("rev-parse", "--abbrev-ref", "HEAD") ?: "local"

fun shortSha(): String =
    (System.getenv("VERSION_SHA")?.takeIf { it.isNotBlank() }
        ?: System.getenv("GITHUB_SHA"))?.take(7)
        ?: git("rev-parse", "--short=7", "HEAD") ?: "nogit"

// True when HEAD sits exactly on a release tag (a clean release build).
fun isTaggedRelease(): Boolean =
    git("describe", "--tags", "--exact-match", "--match", "v[0-9]*") != null

// Clean X.Y.Z on main/tagged builds; X.Y.Z-<branch>.<sha> on branch builds.
fun computeVersionName(): String {
    val (maj, min, pat) = latestSemverTag()
    val base = "$maj.$min.$pat"
    val branch = currentBranch()
    return if (isTaggedRelease() || branch == "main" || branch == "HEAD") base
    else {
        val safe = branch.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').lowercase()
        "$base-$safe.${shortSha()}"
    }
}

// Exposes the computed versionName to CI so the APK filename matches the
// versionName baked into the APK exactly. Usage: `./gradlew -q :app:printVersionName`
tasks.register("printVersionName") {
    doLast { println(computeVersionName()) }
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "com.mountaincrab.learninggames"
    compileSdk = 35

    // CI decodes a base64 keystore secret and points ANDROID_KEYSTORE_PATH at it
    // so debug builds can be signed with a stable key. Falls back to the ephemeral
    // debug key when the secret is absent.
    val keystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    if (keystorePath != null) {
        signingConfigs {
            getByName("debug") {
                storeFile = file(keystorePath)
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    defaultConfig {
        applicationId = "com.mountaincrab.learninggames"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount()
        versionName = computeVersionName()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}
