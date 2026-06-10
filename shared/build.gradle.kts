import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

// Use the Node/Yarn already on the machine instead of letting the Kotlin/JS plugin
// download its own (the download repo it injects is rejected by this build's
// FAIL_ON_PROJECT_REPOS settings, and CI/dev images ship Node anyway).
rootProject.plugins.withType<NodeJsRootPlugin> {
    rootProject.extensions.configure<NodeJsRootExtension> { download = false }
}
rootProject.plugins.withType<YarnPlugin> {
    rootProject.extensions.configure<YarnRootExtension> { download = false }
}

// Game logic + geometry shared between the Android app (JVM) and the webapp (JS).
// The JS target builds an npm-consumable library with TypeScript definitions:
//   ./gradlew :shared:jsBrowserProductionLibraryDistribution
// → shared/build/dist/js/productionLibrary (the webapp depends on it via file:).
kotlin {
    jvm()

    js(IR) {
        moduleName = "learning-games-shared"
        useEsModules()
        browser()
        binaries.library()
        generateTypeScriptDefinitions()
    }

    sourceSets {
        all {
            languageSettings.optIn("kotlin.js.ExperimentalJsExport")
        }
    }
}
