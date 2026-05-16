import com.android.build.api.dsl.ApplicationExtension

plugins {
    id("com.android.application")
}

configure<ApplicationExtension> {
    namespace = "com.example.usbtether"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.usbtether"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")
}
