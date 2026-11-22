import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.toque"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        // Édition Ratatouille : paquet distinct de Toque Smart (app.toque.smart), pour que les deux
        // applis s'installent côte à côte pendant les essais (cahier des charges, EX-02).
        applicationId = "app.ratatouille.smart"
        minSdk = 23          // Android 6.0, celui du Monsieur Cuisine Connect
        targetSdk = 28
        // Usage privé : jamais publiée. Mise à jour seulement depuis le PC (EX-60).
        versionCode = 4
        versionName = "1.0.0"
    }

    signingConfigs {
        // Clé du projet, publique exprès : toutes les compilations (PC, GitHub) portent la même
        // signature, donc chaque mise à jour s'installe par-dessus la précédente.
        create("toque") {
            storeFile = file("signature/toque.p12")
            storeType = "PKCS12"
            storePassword = "toque-public"
            keyAlias = "toque"
            keyPassword = "toque-public"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("toque")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("toque")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")

    packaging {
        // Fichiers de licence en double dans les dépendances du SDK Anthropic (« Cuisiner avec l'IA »).
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/INDEX.LIST",
            "META-INF/*.kotlin_module",
        )
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

dependencies {
    implementation(project(":core"))
    // « Cuisiner avec l'IA » (EX-70) : SDK Java officiel d'Anthropic. Utilisé seulement à partir
    // d'Android 8.0 (API 26, java.time) ; le robot est en 8.1. Pas d'AndroidX.
    implementation("com.anthropic:anthropic-java:2.34.0")
}
