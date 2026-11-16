plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Die Version steht nur in gradle.properties (APP_VERSION=1.2.3).
// versionCode wird daraus berechnet: 1.2.3 -> 10203.
val appVersion: String = providers.gradleProperty("APP_VERSION").get()
val appVersionCode: Int = appVersion.split(".").map(String::toInt)
    .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

// GitHub-Repo, in dem die App nach Updates sucht.
val githubRepo = "peter1965p/PlexCloud"

// Signier-Schlüssel liegt außerhalb des Repos, Zugangsdaten in ~/.gradle/gradle.properties.
val keystoreFile: String? = providers.gradleProperty("PLEXCLOUD_KEYSTORE_FILE").orNull

android {
    namespace = "com.paeffgen.plexcloud"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.paeffgen.plexcloud"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersion
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        // Öffentliche Kennungen (kein Geheimnis): Plexora-API und Cognito-App-Client ohne Client-Secret.
        buildConfigField("String", "API_BASE", "\"https://7hrkm580pb.execute-api.eu-central-1.amazonaws.com\"")
        buildConfigField("String", "COGNITO_DOMAIN", "\"eu-central-1lm7sn6lvc.auth.eu-central-1.amazoncognito.com\"")
        buildConfigField("String", "COGNITO_CLIENT_ID", "\"1aa9chqqkgr9dp232cgpa4nanb\"")
        buildConfigField("String", "REDIRECT_URI", "\"plexcloud://auth/callback\"")
        manifestPlaceholders["appAuthRedirectScheme"] = "plexcloud"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.gradleProperty("PLEXCLOUD_KEYSTORE_PASSWORD").get()
                keyAlias = providers.gradleProperty("PLEXCLOUD_KEY_ALIAS").get()
                keyPassword = providers.gradleProperty("PLEXCLOUD_KEYSTORE_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            // Debug-Version kann neben der echten App installiert sein.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = true
            }
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.browser)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
