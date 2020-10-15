plugins {
    id("com.android.library")
    kotlin("android")
}

kotlin {
    explicitApi()
}

android {
    compileSdkVersion(31)
    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(31)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(Lib.kotlinLang)

    api(Lib.KotlinX.coroutinesCore)
    api(Lib.KotlinX.coroutinesAndroid)

    api(Lib.mqtt)

    testImplementation("junit:junit:4.13.1")
}
