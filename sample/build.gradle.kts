plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}
android {
    compileSdkVersion(30)
    defaultConfig {
        applicationId = "com.sorinirimies.mqttcoroutines.sample"
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

    api(project(":mqttcoroutines"))

    implementation(Lib.AndroidX.appcompat)
    implementation(Lib.AndroidX.activityKtx)
    implementation(Lib.AndroidX.constraintLayout)

    testImplementation("junit:junit:4.13.1")
    androidTestImplementation("com.android.support.test:runner:1.0.2")
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn"
    )
}
