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
        versionCode = 16
        versionName = "1.6.0"
    }

    signingConfigs {
        create("release") {
            // 绛惧悕鍑嵁涓嶈繘浠撳簱锛氫粠鐜鍙橀噺锛圕I锛夋垨 local.properties锛堟湰鍦帮級璇诲彇銆?
            // 鏈厤缃椂浜у嚭鏈鍚嶅寘锛岀敱 CI 鐨?apksigner 姝ラ绛惧悕锛堝惈瀵嗛挜杞崲璋辩郴锛夈€?
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
    // 鍐呭祵瀹樻柟 chat-demo 闈欐€侀〉锛歐ebViewAssetLoader 缁欏畠涓€涓甯告潵婧愶紝
    // 鍚﹀垯 file:// 涓?IndexedDB/localStorage 涓庣浉瀵?chunk 閮戒笉鍙潬
    implementation(libs.androidx.webkit)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
