plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Firma: el keystore vive en ../keystore/ast-campo.p12. La contraseña NO está en el repositorio:
// se lee de la variable de entorno AST_KEYSTORE_PASSWORD o de la propiedad Gradle astKeystorePassword.
val keystorePassword: String? =
    System.getenv("AST_KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
        ?: (findProperty("astKeystorePassword") as String?)?.takeIf { it.isNotBlank() }
val keystoreFile = rootProject.file("keystore/ast-campo.p12")

android {
    namespace = "co.ast.campo"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "co.ast.campo"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
        resourceConfigurations += listOf("es")
    }

    signingConfigs {
        if (keystorePassword != null && keystoreFile.exists()) {
            create("ast") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "ast-campo"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("ast")?.let { signingConfig = it }
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

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/**")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.exifinterface)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
}
