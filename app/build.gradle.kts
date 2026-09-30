import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// Stabil imza: Android bir APK'yı ancak kurulu sürümle AYNI anahtarla imzalanmışsa güncelleme
// olarak kabul eder. İmza bilgileri repoya GİRMEZ (repo herkese açık - anahtarı ele geçiren
// bizim adımıza "güncelleme" yayınlayabilir). Kaynak sırası:
//   1) CI: GitHub Secrets'tan gelen ortam değişkenleri (SIGNING_KEYSTORE_PATH, SIGNING_PASSWORD)
//   2) Yerel: app/keystore.properties (bkz. app/keystore.properties.example)
// İkisi de yoksa geçici debug anahtarına düşülür - APK çalışır ama kurulu sürümün üzerine
// güncelleme olarak YÜKLENEMEZ.
val keystorePropertiesFile = rootProject.file("app/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(propName)?.takeIf { it.isNotBlank() }

val signingStoreFile = signingValue("SIGNING_KEYSTORE_PATH", "storeFile")
val hasStableSigning = signingStoreFile != null

// Android yalnızca versionCode'u DAHA BÜYÜK bir APK'yı güncelleme sayar. CI'da her çalıştırmanın
// numarası bir öncekinden büyük olduğu için onu kullanıyoruz; yerel derlemeler 1'de kalır.
val ciBuildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val appVersionBase = providers.gradleProperty("appVersionBase").get()

android {
    namespace = "com.cruciblelab.trafficlogger"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cruciblelab.trafficlogger"
        minSdk = 26
        targetSdk = 34
        versionCode = ciBuildNumber ?: 1
        versionName = if (ciBuildNumber != null) "$appVersionBase.$ciBuildNumber" else "$appVersionBase.0-dev"
    }

    signingConfigs {
        if (hasStableSigning) {
            create("stable") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingValue("SIGNING_PASSWORD", "storePassword")
                keyAlias = signingValue("SIGNING_KEY_ALIAS", "keyAlias") ?: "canliagtrafigi"
                keyPassword = signingValue("SIGNING_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasStableSigning) "stable" else "debug")
        }
        debug {
            isMinifyEnabled = false
            // Yerelde keystore varsa debug da aynı anahtarla imzalanır; böylece yerel bir
            // derleme, CI'dan kurulmuş sürümün üzerine (ya da tersi) güncelleme olarak yüklenebilir.
            if (hasStableSigning) signingConfig = signingConfigs.getByName("stable")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-process:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
