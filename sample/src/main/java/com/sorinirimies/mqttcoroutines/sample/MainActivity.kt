package com.sorinirimies.mqttcoroutines.sample

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.sorinirimies.mqttcoroutines.MqttCoroutineManager

class MainActivity : AppCompatActivity() {
    private val mqttManager by lazy {
        MqttCoroutineManager(
            "",
            Settings.Secure.getString(
                this.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mqttManager.connect(arrayOf("vehicle/color"), intArrayOf(0)).also { mqttConnected ->
            if (mqttConnected) {
                mqttManager.subscribeToMqttPayload { mqttPayload ->
                    Log.i(
                        MainActivity::class.java.simpleName,
                        "Mqtt payload is: ${mqttPayload.first} ${mqttPayload.second}"
                    )
                }
                mqttManager.subscribeToMqttConnectionState { connectionState ->
                    Log.i(MainActivity::class.java.simpleName, "Connection state is: $connectionState")
                }
            }
        }
    }
}

