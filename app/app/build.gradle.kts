plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    kotlin("plugin.serialization") version "1.9.22"
}

android {
    namespace = "com.scrudio.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scrudio.tv"
        minSdk = 24            // Android 7.0 — covers every modern Android TV
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // ARM64 only — TCL 40S5400A is arm64-v8a; saves ~4 MB on the APK
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        vectorDrawables.useSupportLibrary = true

        // ── Bundled API keys (debug-only defaults) ─────────────────────────
        // Real users override these in-app via the Settings screen (DataStore).
        buildConfigField("String", "TMDB_DEFAULT_KEY", "\"3384c7898fd56a83dde3bff5e665a6ef\"")
        buildConfigField("String", "TMDB_BASE_URL", "\"https://api.themoviedb.org/3/\"")
        buildConfigField("String", "TORRENTIO_BASE", "\"https://torrentio.strem.fun\"")
        // Public Real-Debrid client_id used for the open-source device flow.
        // Same value Stremio, Kodi addons and rdtcli use.
        buildConfigField("String", "RD_CLIENT_ID", "\"X245A4XAIBGVM\"")
        buildConfigField("String", "RD_BASE_URL", "\"https://api.real-debrid.com/\"")
        // OpenSubtitles API key for subtitle support
        buildConfigField("String", "OPENSUBTITLES_API_KEY", "\"yAW0dt0RQ6IWlQwy8kMZMaVLwOiX56Qs\"")
        buildConfigField("String", "OPENSUBTITLES_BASE_URL", "\"https://api.opensubtitles.com/api/v1\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false        // Sage v2 commandment #7 — R8 broke things in v2; off until proven
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "/kotlin/**",
                "**.kotlin_module"
            )
        }
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
}

dependencies {
    // ── AndroidX core ──────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")

    // ── Leanback (the heart of Android TV UX) ──────────────────────────────
    implementation("androidx.leanback:leanback:1.0.0")
    // 1.2.0-alpha04 is required for the *Compat fragments (host with FragmentActivity).
    // Stable 1.0.0 still ships only platform-Fragment versions (deprecated).
    implementation("androidx.leanback:leanback-preference:1.2.0-alpha04")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // ── Image loading (lighter than Glide, async-first) ────────────────────
    implementation("io.coil-kt:coil:2.5.0")

    // ── QR code generation for Real-Debrid pairing screen ──────────────────
    // Pure-Java lib (~580 KB), no dependencies. We render to Bitmap ourselves.
    implementation("com.google.zxing:core:3.5.2")

    // ── Coroutines ─────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ── Network: Retrofit + OkHttp + kotlinx-serialization ─────────────────
    // Wired in for Phase 2 (data layer); declared now so build verifies.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    // ── Persistence: DataStore (modern replacement for SharedPreferences) ──
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ── Media3 (ExoPlayer) for playback ────────────────────────────────────
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")
    implementation("androidx.media3:media3-ui-leanback:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")

    // ── Tests ──────────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}
