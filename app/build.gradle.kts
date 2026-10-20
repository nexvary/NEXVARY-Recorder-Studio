import java.util.Base64

plugins {
    id("com.android.application")
}

android {
    namespace = "com.nexvary.recorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nexvary.recorder"
        minSdk = 29
        targetSdk = 35
        versionCode = 10
        versionName = "0.7.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // PUBLIC development key only. Pin the path explicitly: CI/Gradle user homes differ.
    val previewKey = rootProject.file(".github/preview-debug.keystore")
    previewKey.writeBytes(Base64.getMimeDecoder().decode(rootProject.file(".github/preview-debug-key.b64").readText()))
    signingConfigs.getByName("debug") {
        storeFile = previewKey
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        animationsDisabled = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*"
        )
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
