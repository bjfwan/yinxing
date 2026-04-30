import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}
val releaseStorePath = localProps.getProperty("RELEASE_STORE_FILE")?.takeIf { it.isNotBlank() } ?: "release-key.jks"
val releaseStoreFile = rootProject.file(releaseStorePath)
val releaseKeyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "mykey"
val releaseStorePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
val releaseKeyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile.exists() &&
    releaseKeyAlias.isNotBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

gradle.taskGraph.whenReady {
    val releasePackageRequested = allTasks.any {
        it.path == ":app:assembleRelease" || it.path == ":app:bundleRelease"
    }
    if (releasePackageRequested && !hasReleaseSigning) {
        throw GradleException(
            "Release signing requires RELEASE_STORE_FILE, RELEASE_KEY_ALIAS, RELEASE_STORE_PASSWORD and RELEASE_KEY_PASSWORD"
        )
    }
}

android {
    namespace = "com.yinxing.launcher"
    compileSdk = 36

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.yinxing.launcher"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SENIVERSE_UID", "\"${localProps["SENIVERSE_UID"] ?: ""}\"")
        buildConfigField("String", "SENIVERSE_PK",  "\"${localProps["SENIVERSE_PK"]  ?: ""}\"")
        buildConfigField("String", "TENCENT_KEY",   "\"${localProps["TENCENT_KEY"]   ?: ""}\"")
        buildConfigField("String", "LOBSTER_UPLOAD_URL",   "\"${localProps["LOBSTER_UPLOAD_URL"]   ?: ""}\"")
        buildConfigField("String", "LOBSTER_UPLOAD_TOKEN", "\"${localProps["LOBSTER_UPLOAD_TOKEN"] ?: ""}\"")
    }

    signingConfigs {
        create("release") {
            storeFile = releaseStoreFile
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 本地构建时不上传 Crashlytics mapping，避免连不上 Google 服务导致失败
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        animationsDisabled = true
        unitTests.isIncludeAndroidResources = true
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.localbroadcastmanager)
    implementation(libs.androidx.datastore.preferences)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)
    baselineProfile(project(":benchmark"))
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
}
