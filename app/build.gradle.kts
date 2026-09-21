plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseStoreFile = providers.environmentVariable("ILYRO_RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("ILYRO_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ILYRO_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ILYRO_RELEASE_KEY_PASSWORD").orNull
val releaseSigningReady = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.ilyro.browser"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "com.ilyro.browser"; minSdk = 26; targetSdk = 36
        versionCode = 144; versionName = "0.26.0-rc6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "ILYRO"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { compose = true }
    androidResources { ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:!CVS:!thumbs.db:!picasa.ini:!*~" }
    signingConfigs {
        if (releaseSigningReady) create("ilyroRelease") {
            storeFile = rootProject.file(releaseStoreFile!!); storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias; keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        debug { applicationIdSuffix = ".dev"; manifestPlaceholders["appLabel"] = "ILYRO Dev" }
        release {
            isMinifyEnabled = true; isShrinkResources = true
            if (releaseSigningReady) signingConfig = signingConfigs.getByName("ilyroRelease")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs { useLegacyPackaging = true }; resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}

kotlin {
    compilerOptions {
        optIn.add("org.mozilla.geckoview.ExperimentalGeckoViewApi")
    }
}

// Kotlin/Compose browser source is canonical in git. preBuild validates the
// Gecko/Compose overlay contract but never rewrites application source.
val prepareBundledAdBlock by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("python3", rootProject.file("scripts/prepare_ublock.py").absolutePath)
}
val verifyNativeGeckoOverlayContract by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("python3", rootProject.file("scripts/verify_native_gecko_overlay_contract.py").absolutePath)
    dependsOn(prepareBundledAdBlock)
}
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(verifyNativeGeckoOverlayContract)
}

tasks.configureEach {
    val isReleasePackagingTask = name.startsWith("assembleRelease", true) || name.startsWith("bundleRelease", true) || name.startsWith("packageRelease", true)
    if (isReleasePackagingTask) doFirst { check(releaseSigningReady) { "ILYRO release signing is not configured. Set ILYRO_RELEASE_STORE_FILE, ILYRO_RELEASE_STORE_PASSWORD, ILYRO_RELEASE_KEY_ALIAS and ILYRO_RELEASE_KEY_PASSWORD." } }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    implementation("com.google.android.gms:play-services-auth:21.5.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // Stable GeckoView release published by Mozilla's Maven repository.
    implementation("org.mozilla.geckoview:geckoview:155.0.20260903215306")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
