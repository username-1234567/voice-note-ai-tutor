import java.util.Properties
import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.suitanglu.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.suitanglu.app"
        minSdk = 29
        targetSdk = 35
        versionCode = 12
        versionName = "1.5.0"
    }

    signingConfigs {
        create("release") {
            // 签名凭据不进仓库：从环境变量（CI）或 local.properties（本地）读取。
            // 未配置时产出未签名包，由 CI 的 apksigner 步骤签名（含密钥轮换谱系）。
            val props = Properties()
            val lp = rootProject.file("local.properties")
            if (lp.exists()) lp.inputStream().use { props.load(it) }
            val storePath = System.getenv("SIGNING_STORE_FILE") ?: props.getProperty("signing.storeFile")
            val storePass = System.getenv("SIGNING_STORE_PASSWORD") ?: props.getProperty("signing.storePassword")
            val alias = System.getenv("SIGNING_KEY_ALIAS") ?: props.getProperty("signing.keyAlias") ?: "suitanglu-v2"
            if (storePath != null && storePass != null && File(storePath).exists()) {
                storeFile = File(storePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = storePass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val sc = signingConfigs.getByName("release")
            if (sc.storeFile != null) signingConfig = sc
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.common)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.ucrop)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
