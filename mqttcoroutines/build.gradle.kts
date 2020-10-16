plugins {
    id("com.android.library")
    id("github-publish")
    kotlin("android")
}

kotlin {
    explicitApi()
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
