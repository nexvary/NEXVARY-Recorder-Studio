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
        versionCode = 17
        versionName = "0.10.1"
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
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("test").kotlin.srcDir("src/testSupport/java")
        getByName("androidTest").kotlin.srcDir("src/testSupport/java")
    }

    testOptions {
        animationsDisabled = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/LICENSE.md",
            "META-INF/NOTICE.md"
        )
    }
}

dependencies {
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.github.pedroSG94.RootEncoder:library:2.8.1")

    implementation("com.android.billingclient:billing:8.3.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("org.eclipse.angus:jakarta.mail:2.0.5")
    implementation("org.eclipse.angus:angus-activation:2.0.2")
    implementation("jakarta.activation:jakarta.activation-api:2.1.3")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
