plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Читаем версии из gradle.properties с резервными значениями
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0.0"
val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "ua.svitlo"
    compileSdk = 34

    defaultConfig {
        applicationId = "ua.svitlo"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        buildConfigField("int", "APP_VERSION_CODE", appVersionCode.toString())
        buildConfigField("String", "APP_VERSION_NAME", "\"$appVersionName\"")
    }

    signingConfigs {
        create("svitlo") {
            storeFile = file("svitlo.p12")
            storePassword = "svitlo123" // ⚠️ Убедитесь, что этот пароль точно совпадает с паролем от вашего файла svitlo.p12!
            keyAlias = "svitlo"
            keyPassword = "svitlo123"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("svitlo")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("svitlo")
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
}
