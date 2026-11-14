/*
 * Renders the SubVenty terminal, ICEBREAKER, loot and screensaver surfaces with Compose for Desktop and runs their unit tests,
 * using only Maven Central. Cloud containers can't reach Google Maven, so this is how the night UI
 * gets compile-checked and screenshotted there, in the preview-2 SubSeven chrome. It copies the pure-Compose app files (no Android
 * types) at build time; it is a design-review aid, not a substitute for the Android build.
 *
 *   ./gradlew -p tools/night-preview run    # PNGs in tools/night-preview/out
 *   ./gradlew -p tools/night-preview test   # TerminalModel/Icebreaker tests on the JVM
 *
 * Compose 1.5 is the newest desktop line with no Google-Maven-only androidx dependencies.
 */
plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.compose") version "2.3.21"
    application
}

kotlin {
    jvmToolchain(21)
    compilerOptions { freeCompilerArgs.addAll("-opt-in=androidx.compose.ui.text.ExperimentalTextApi") }
}

val cmp = "1.5.12"
dependencies {
    implementation("org.jetbrains.compose.runtime:runtime-desktop:$cmp")
    implementation("org.jetbrains.compose.ui:ui-desktop:$cmp")
    implementation("org.jetbrains.compose.foundation:foundation-desktop:$cmp")
    implementation("org.jetbrains.compose.material3:material3-desktop:$cmp")
    implementation("org.jetbrains.compose.material:material-icons-extended-desktop:$cmp")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.7.85.4")
    testImplementation("junit:junit:4.13.2")
}

val appSrc = rootDir.resolve("../../app/src")
val pkg = "io/github/orth4x0r/venty/ui"
val pureFiles = listOf("Temperature.kt") + listOf("NightTheme", "TerminalModel", "Icebreaker", "TerminalHome", "NightPieces",
    "SubVentyChrome").map { "subventy/$it.kt" }

val syncMain by tasks.registering(Sync::class) {
    from(appSrc.resolve("main/java/$pkg")) {
        include(pureFiles)
        // The one API the app uses that Compose 1.5 spells differently.
        filter { it.replace("autoCorrectEnabled = false", "autoCorrect = false") }
    }
    into(layout.buildDirectory.dir("app-src/main"))
}
val syncTest by tasks.registering(Sync::class) {
    from(appSrc.resolve("test/java/$pkg")) { include("subventy/TerminalModelTest.kt", "subventy/IcebreakerTest.kt") }
    into(layout.buildDirectory.dir("app-src/test"))
}
sourceSets {
    main { kotlin.srcDir(syncMain) }
    test { kotlin.srcDir(syncTest) }
}

application { mainClass.set("harness.RenderKt") }
tasks.named<JavaExec>("run") { workingDir = projectDir }
