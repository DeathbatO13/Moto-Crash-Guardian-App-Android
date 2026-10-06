import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.protobuf)
}

// Firma de release: keystore.properties local (ignorado por Git) o variables de entorno en CI.
// Ver docs/DISTRIBUCION_APK.md. Sin estos valores el APK release sale sin firmar y no se puede instalar.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(key: String, env: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(env)

val uploadStoreFile = signingValue("storeFile", "MCG_UPLOAD_STORE_FILE")

val apiBaseUrl = providers.gradleProperty("mcg.apiBaseUrl").get()
require(apiBaseUrl.startsWith("https://") && apiBaseUrl.endsWith("/")) {
    "mcg.apiBaseUrl debe ser HTTPS y terminar en '/': $apiBaseUrl"
}
val appVersionCode = providers.gradleProperty("mcg.versionCode").get().toInt()
val appVersionName = providers.gradleProperty("mcg.versionName").get()

android {
    namespace = "com.motocrashguardian"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.motocrashguardian"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(uploadStoreFile)
                storePassword = signingValue("storePassword", "MCG_UPLOAD_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "MCG_UPLOAD_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "MCG_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Backend local desde el emulador; cleartext solo permitido en src/debug.
            buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8080/\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric necesita los recursos/manifest mergeados.
            isIncludeAndroidResources = true
            all { it.useJUnitPlatform() }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                maybeCreate("java").option("lite")
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.protobuf.javalite)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.nordic.ble.ktx)
    implementation(libs.play.services.location)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Pruebas JVM (docs/09-testing-strategy.md): JUnit 5 para logica pura; Robolectric y
    // Compose UI Test siguen en JUnit 4 y corren sobre la misma plataforma via Vintage.
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit5.vintage.engine)
    testRuntimeOnly(libs.junit5.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
