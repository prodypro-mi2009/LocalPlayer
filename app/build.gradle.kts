import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// --- Release signing -------------------------------------------------------------------------
// The keystore stays outside the project. Its path and signing credentials come from a local, Git-ignored
// `keystore.properties`, or its path can be supplied through LOCALPLAYER_RELEASE_KEYSTORE.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use { load(it) }
}
val releaseKeystorePath = keystoreProperties.getProperty("storeFile")?.trim()?.takeIf { it.isNotEmpty() }
    ?: System.getenv("LOCALPLAYER_RELEASE_KEYSTORE")?.trim()?.takeIf { it.isNotEmpty() }
val releaseKeystoreFile = releaseKeystorePath?.let { rootProject.file(it) }
private fun signingValue(name: String): String? = keystoreProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
val releaseSigningProblems: List<String> = buildList {
    if (!keystorePropertiesFile.isFile) add("keystore.properties was not found in ${rootProject.projectDir}")
    for (name in listOf("storePassword", "keyAlias", "keyPassword")) {
        if (signingValue(name) == null) add("'$name' is missing or empty in keystore.properties")
    }
    if (releaseKeystoreFile == null) add("the keystore path is missing (set storeFile in keystore.properties or LOCALPLAYER_RELEASE_KEYSTORE)")
    else if (!releaseKeystoreFile.isFile) add("the configured keystore file was not found")
}

android {
    namespace = "com.localplayer.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.localplayer.music"
        minSdk = 26
        targetSdk = 36
        versionCode = 167
        versionName = "2.0.0"

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
                storeFile = releaseKeystoreFile!!
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
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = true
        // arm64 only on purpose (see abiFilters): the app is built for phones.
        disable += "ChromeOsAbiSupport"
        abortOnError = true
        lintConfig = file("lint.xml")
    }

    sourceSets {
        // Exported Room schemas (1..n.json) are used by the migration test.
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    packaging {
        // yt-dlp's Python and FFmpeg are executed straight from the app's native library folder.
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    // Installs the baseline profile on devices/installs that do not get it from the store (sideloaded APKs).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    // Keep the runtime aligned with Room's schema serializer (room-migration uses Serialization 1.8.1).
    // Lifecycle currently requests core 1.7.3; AGP carries main runtime versions into androidTest.
    implementation(platform("org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    val room = "2.8.5"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Real tag reading/writing inside the audio files (MP3 ID3, FLAC Vorbis comments, MP4 atoms).
    // Android fork 2.3.15: uses java.io.File, so edits are done on a verified temporary copy.
    implementation("com.github.Adonai:jaudiotagger:2.3.15")

    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")

    // yt-dlp for Android (Python + FFmpeg are bundled as native libraries): the "Download from YouTube" screen.
    // NOTE: ffmpeg 0.18.1 is NOT used on purpose: it ships without libc++_shared.so (needed by librubberband.so), so FFmpeg
    // cannot start (upstream issue yausername/youtubedl-android#369). 0.17.2 works but its libffmpeg.so is not 16 KB aligned
    // (lint Aligned16KB). Move to the next ffmpeg release once that issue is fixed.
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.17.2")

    // Jam: the QR code of a session (pure Java, no camera) and the system's own QR scanner for joining (no camera
    // permission needed; it needs Google Play services, so joining can also be done by typing the address and code).
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is only a stub in local unit tests; this is the real implementation (tests only).
    testImplementation("org.json:json:20231013")

    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:$room")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
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
                "\nCreate keystore.properties in the project root (it is Git-ignored) with " +
                "storePassword, keyAlias, keyPassword and storeFile, using the values of your own keystore. " +
                "Debug builds are not affected.",
        )
    }
}
