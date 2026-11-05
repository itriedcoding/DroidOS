plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.droidos.shell"
    compileSdk = 35
    // Pin to the build-tools this project installs, so builds never depend on whatever
    // AGP decides to download at configure time.
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "dev.droidos.shell"
        minSdk = 30
        targetSdk = 35
        versionCode = 7
        versionName = "1.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        // A stable debug keystore lives in .tools so repeated builds keep the same
        // signing identity. The integrity verifier pins this certificate at runtime.
        create("droidos") {
            storeFile = file(System.getenv("DROIDOS_KEYSTORE") ?: "${rootDir}/.tools/keystore/droidos.jks")
            storePassword = System.getenv("DROIDOS_KEYSTORE_PASSWORD") ?: "droidos-dev"
            keyAlias = System.getenv("DROIDOS_KEY_ALIAS") ?: "droidos"
            keyPassword = System.getenv("DROIDOS_KEY_PASSWORD") ?: "droidos-dev"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ""
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("droidos")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the same identity so the produced APK is installable as-is.
            signingConfig = signingConfigs.getByName("droidos")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    sourceSets {
        getByName("main") {
            // The platform interfaces are compiled from platform/aidl, so a device image and the
            // shell share one definition instead of two copies that drift apart.
            aidl.srcDir("${rootDir}/platform/aidl")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
    }

    lint {
        abortOnError = false
        warningsAsErrors = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
