plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.firebase.firebase.perf)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.ikhaya_accomadationapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.ikhaya_accomadationapp"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.perf)
    implementation(libs.firebase.firestore)
    implementation(libs.material)
    implementation("com.google.android.gms:play-services-location:21.2.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}