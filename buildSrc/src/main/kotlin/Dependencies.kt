object Lib {
    const val mqtt = "org.eclipse.paho:org.eclipse.paho.client.mqttv3:${Version.mqtt}"
    const val kotlinLang = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Version.kotlinVersion}"

    object AndroidX {
        const val activityKtx = "androidx.activity:activity-ktx:${Version.AndroidX.activityKtx}"
        const val appcompat = "androidx.appcompat:appcompat:${Version.AndroidX.appcompat}"
    }

    object KotlinX {
        const val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Version.coroutines}"
        const val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Version.coroutines}"
    }
}