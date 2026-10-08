import com.android.build.api.artifact.SingleArtifact

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.falakpatel.stridelocal"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.falakpatel.stridelocal"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.6.2-alpha"
        resourceConfigurations += "en" // drop unused library translations (smaller APK)
    }

    // Fixed release key (from GitHub secrets) so updates install over the old version
    // and your local data is kept. Falls back to the debug key when the secret is absent.
    val keystorePath: String? = System.getenv("STRIDE_KEYSTORE")
    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("STRIDE_KEYSTORE_PASSWORD")
                keyAlias = "stridelocal"
                keyPassword = System.getenv("STRIDE_KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (keystorePath != null) "release" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false }
    packaging {
        resources.excludes += listOf("/META-INF/*.version", "/META-INF/*.kotlin_module", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended") // walking and running symbols (unused icons are stripped from the APK)
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Local storage only
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Optional one-time import from Health Connect (on-device store used by Google Fit / Health)
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Home screen widget
    implementation("androidx.glance:glance-appwidget:1.1.1")

    testImplementation("junit:junit:4.13.2")
}

/**
 * Privacy guard: fails the build if any library sneaks a network permission
 * into the merged manifest. Runs before every assemble task.
 */
abstract class VerifyNoNetworkPermission : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val text = manifest.get().asFile.readText()
        val banned = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
        )
        val found = banned.filter { text.contains("\"$it\"") }
        if (found.isNotEmpty()) throw GradleException("Network permission found in merged manifest: $found")
        report.get().asFile.writeText("OK: no network permissions")
    }
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val variantName = variant.name
        val verify = tasks.register<VerifyNoNetworkPermission>("verify${cap}NoNetwork") {
            manifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
            report.set(layout.buildDirectory.file("reports/no-network-$variantName.txt"))
        }
        tasks.matching { it.name == "assemble$cap" }.configureEach { dependsOn(verify) }
    }
}
