// AGP 9 compiles Kotlin itself (built-in Kotlin). This line picks the Kotlin version it uses.
// It must match the Compose compiler plugin version below.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21") }
}

plugins {
    id("com.android.application") version "9.5.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.google.devtools.ksp") version "2.3.14" apply false
}
