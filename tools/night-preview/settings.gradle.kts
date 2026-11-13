// A standalone JVM build, separate from the Android app: run it with ./gradlew -p tools/night-preview <task>.
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "night-preview"
