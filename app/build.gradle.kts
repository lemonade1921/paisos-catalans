import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cat.paisoscatalans"
    compileSdk = 37

    defaultConfig {
        applicationId = "cat.paisoscatalans"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.0"
    }

    signingConfigs {
        // Clau pròpia si es defineixen els secrets SIGNATURA_* (vegeu el README); si no, una clau
        // de desenvolupament fixa, inclosa al repositori, perquè totes les compilacions tinguin la
        // mateixa signatura i l'app es pugui actualitzar sense desinstal·lar-la.
        create("publicacio") {
            val fitxer = System.getenv("SIGNATURA_FITXER")
            if (fitxer != null && file(fitxer).exists()) {
                storeFile = file(fitxer)
                storePassword = System.getenv("SIGNATURA_CONTRASENYA")
                keyAlias = System.getenv("SIGNATURA_ALIES")
                keyPassword = System.getenv("SIGNATURA_CONTRASENYA_CLAU") ?: System.getenv("SIGNATURA_CONTRASENYA")
            } else {
                storeFile = file("signatura-desenvolupament.jks")
                storePassword = "paisoscatalans"
                keyAlias = "paisoscatalans"
                keyPassword = "paisoscatalans"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("publicacio")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("publicacio")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    // Privadesa: no incloure metadades de dependències xifrades per Google a l'APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
}
