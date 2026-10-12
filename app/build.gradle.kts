import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// --- Release signing -------------------------------------------------------------------------
// The keystore stays where it is (outside the project). Only its location is written here; the alias and
// both passwords are read from a local, Git-ignored `keystore.properties` in the project root.
val releaseKeystoreFile = file("C:/AndroidKeys/myergani-release.jks")
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}
private fun signingValue(name: String): String? = keystoreProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
val releaseSigningProblems: List<String> = buildList {
    if (!keystorePropertiesFile.isFile) add("keystore.properties was not found in ${rootProject.projectDir}")
    for (name in listOf("storePassword", "keyAlias", "keyPassword")) {
        if (signingValue(name) == null) add("'$name' is missing or empty in keystore.properties")
    }
    if (!releaseKeystoreFile.isFile) add("the keystore file was not found at ${releaseKeystoreFile.path}")
}

android {
    namespace = "com.localplayer.music"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.localplayer.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.4"

        // ARM64 first. There is no native code in this app yet, so this only
        // guarantees that no unneeded architectures are ever packaged.
        ndk {
            abiFilters += "arm64-v8a"
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Only defined when everything is present, so a half-configured setup can never produce a signed APK.
        if (releaseSigningProblems.isEmpty()) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Never the debug key. Without a complete release config the build is stopped (see below).
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    sourceSets {
        // Exported Room schemas (1..n.json) are used by the migration test.
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    val composeBom = platform("androidx.compose:compose-bom:2025.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    val room = "2.8.3"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Real tag reading/writing inside the audio files (MP3 ID3, FLAC Vorbis comments, MP4 atoms).
    // Android fork 2.3.15: uses java.io.File, so edits are done on a verified temporary copy.
    implementation("com.github.Adonai:jaudiotagger:2.3.15")

    val media3 = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.room:room-testing:$room")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

// Debug builds and plain compilation work without any signing data. Anything that would PACKAGE or INSTALL a
// release build is refused, with a message that never contains secret values.
gradle.taskGraph.whenReady {
    val packagesRelease = allTasks.any {
        it.project == project &&
            Regex("^(assemble|bundle|package|install|validateSigning)Release.*").matches(it.name)
    }
    if (packagesRelease && releaseSigningProblems.isNotEmpty()) {
        throw GradleException(
            "Release signing is not set up:\n" +
                releaseSigningProblems.joinToString("\n") { " - $it" } +
                "\nCreate keystore.properties in the project root (it is Git-ignored) with the keys " +
                "storePassword, keyAlias and keyPassword, using the values of your own keystore " +
                "(${releaseKeystoreFile.path}). Debug builds are not affected.",
        )
    }
}
