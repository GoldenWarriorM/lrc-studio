import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    jvm {
        // Pin the desktop bytecode level so the build does not depend on the
        // JDK that happens to run Gradle (the app must start on Java 17+).
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    sourceSets {
        // Android and the desktop app both run on the JVM, so the syllable
        // hyphenation engine is compiled once for the two of them.
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
        }
        getByName("androidMain").dependsOn(jvmSharedMain)
        getByName("jvmMain").dependsOn(jvmSharedMain)

        jvmSharedMain.dependencies {
            // Only the hyphenation engine is used, so skip FOP's rendering stack.
            implementation("org.apache.xmlgraphics:fop-core:${libs.versions.fop.get()}") {
                isTransitive = false
            }
        }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.animation)
            implementation(compose.components.resources)
            implementation(compose.materialIconsExtended)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.serialization.json)
        }

        androidMain.dependencies {
            implementation(libs.activity.compose)
            implementation(libs.koin.android)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
            implementation(libs.kotlinx.coroutines.android)
        }

        named("jvmMain") {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.versions.coroutines.get()}")
                implementation("net.java.dev.jna:jna:5.14.0")
            }
        }
    }
}

android {
    namespace = "com.lrcstudio.app"
    compileSdk = 35

    // TeX hyphenation patterns live in the shared JVM source set; package them
    // with the APK as classpath resources.
    sourceSets.getByName("main").resources.srcDir("src/jvmSharedMain/resources")

    defaultConfig {
        applicationId = "com.lrcstudio.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    if (keystorePropsFile.exists()) {
        val props = Properties().apply {
            load(keystorePropsFile.inputStream())
        }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(props["storeFile"] as String)
                storePassword = props["storePassword"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.lrcstudio.app.MainKt"
        buildTypes {
            release {
                proguard {
                    isEnabled.set(false)
                }
            }
        }
    }
}
