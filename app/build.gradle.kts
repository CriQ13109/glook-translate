plugins {
    alias(libs.plugins.android.application)
    // AGP 9.x 는 Kotlin 지원이 내장되어 있어 org.jetbrains.kotlin.android 플러그인을 별도로 적용하지 않습니다.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.glook.translate"
    // compose 1.13 (BOM 2026.11.01) 은 compileSdk 37.1 이상을 요구 (AAR 메타데이터)
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "app.glook.translate"
        // 29: android.icu.text.Transliterator(로마자/병음 표기) 최소 API
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        // -PnoSecure: FLAG_SECURE 를 아예 적용하지 않는 별도 빌드(스크린샷 확인용)
        buildConfigField("boolean", "ALLOW_SECURE", (!project.hasProperty("noSecure")).toString())
        // 실기기 ABI 만 포함 (ML Kit/SQLCipher 네이티브 라이브러리가 ABI 당 ~30MB)
        ndk { abiFilters += setOf("arm64-v8a") }
    }

    buildTypes {
        release {
            isMinifyEnabled = !project.hasProperty("noMinify")
            isShrinkResources = false
            // 사이드로드용: 디버그 키로 서명(Play 배포용 아님). 설치된 디버그 빌드 위에 그대로 덮어 설치 가능.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.graphicsMode", "NATIVE"); it.maxHeapSize = "3g" }
        }
    }

    // 지원하는 앱 언어만 리소스에 포함 (라이브러리의 다른 언어 문자열 제거)
    androidResources {
        localeFilters += listOf("ko", "en", "ja", "zh-rCN", "es", "fr", "de", "pt", "ru", "vi")
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/native-image/**", "org/bouncycastle/pqc/**")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    // 암호화 저장소
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.androidx.datastore.preferences)
    // androidx.security:security-crypto 는 1.1.0 에서 전체 API 가 @Deprecated 되어 사용하지 않습니다(README 참고).

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // ML Kit (온디바이스)
    implementation(libs.mlkit.translate)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.korean)
    implementation(libs.mlkit.text.recognition.japanese)
    implementation(libs.mlkit.text.recognition.chinese)

    // 위젯
    implementation(libs.androidx.window)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.pdfbox.android)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // BYOK 클라우드 엔진
    implementation(libs.anthropic.java)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(libs.glance.testing)
    debugImplementation(libs.compose.ui.test.manifest)
}
