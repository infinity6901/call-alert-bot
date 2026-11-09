plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.callalertbot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.callalertbot"
        minSdk = 24
        targetSdk = 34
        // CI passes these for tagged releases (-PversionName=1.2.3 -PversionCode=42).
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // The release key comes from the environment, so it never lives in the repo. CI sets these from
    // GitHub secrets for tagged releases; without them, a release build is simply left unsigned.
    signingConfigs {
        val keystorePath = System.getenv("CAB_KEYSTORE_FILE")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CAB_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CAB_KEY_ALIAS")
                keyPassword = System.getenv("CAB_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = false
        htmlReport = true
        xmlReport = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    // Android's own org.json is only a stub in JVM unit tests; this is the real one.
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
