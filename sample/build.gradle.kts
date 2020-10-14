plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}
android {
    compileSdkVersion(31)
    defaultConfig {
        applicationId = "com.sorinirimies.mqttcoroutines.sample"
        minSdkVersion(21)
        targetSdkVersion(31)
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(Lib.kotlinLang)
    implementation(Lib.AndroidX.appcompat)
    implementation(Lib.AndroidX.activityKtx)
    implementation("com.android.support.constraint:constraint-layout:1.1.3")
    testImplementation("junit:junit:4.13.1")
    androidTestImplementation("com.android.support.test:runner:1.0.2")
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.2")
    api(project(":mqttcoroutines"))
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.freeCompilerArgs += listOf(
            "-Xopt-in=kotlin.RequiresOptIn"
    )
}
