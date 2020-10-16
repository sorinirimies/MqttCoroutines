object Lib {
    const val mqtt = "org.eclipse.paho:org.eclipse.paho.client.mqttv3:${Version.mqtt}"
    const val kotlinGradlePlugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Version.kotlinVersion}"

    object AndroidX {
        const val activityKtx = "androidx.activity:activity-ktx:${Version.AndroidX.activityKtx}"
        const val appcompat = "androidx.appcompat:appcompat:${Version.AndroidX.appcompat}"
        const val constraintLayout = "androidx.constraintlayout:constraintlayout:${Version.AndroidX.constraintLayout}"
    }

    object KotlinX {
        const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Version.coroutines}"
        const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Version.coroutines}"
    }

    object Test {
        const val junit4 = "junit:junit:${Version.Test.junit4}"
        const val junit5Api = "org.junit.jupiter:junit-jupiter-api:${Version.Test.junit5}"
        const val kotlinTest = "io.kotlintest:kotlintest-runner-junit5:${Version.Test.kotlinTest}"
    }
}