plugins {
    id("com.android.library")
    id("github-publish")
    kotlin("android")
    id("org.jetbrains.dokka")
}

kotlin {
    explicitApi()
}

buildscript {
    repositories.jcenter()
    dependencies {
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${Version.dokka}")
    }
}

android {
    compileSdkVersion(30)
    defaultConfig {
        minSdkVersion(17)
        targetSdkVersion(30)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(kotlin("stdlib", Version.kotlinVersion))

    api(Lib.KotlinX.coroutinesCore)
    api(Lib.KotlinX.coroutinesAndroid)

    api(Lib.mqtt)

    testImplementation(Lib.Test.junit4)
    testImplementation(Lib.Test.junit5Api)
}

tasks.dokkaHtml.configure {
    outputDirectory.set(buildDir.resolve("dokkaCustomMultiModuleOutput"))
}