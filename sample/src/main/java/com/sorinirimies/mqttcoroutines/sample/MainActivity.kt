package com.sorinirimies.mqttcoroutines.sample

import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.sorinirimies.mqttcoroutines.MqttConnectionStateListener
import com.sorinirimies.mqttcoroutines.MqttCoroutineManager
import com.sorinirimies.mqttcoroutines.MqttPayload
import com.sorinirimies.mqttcoroutines.MqttPayloadListener
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

class MainActivity : AppCompatActivity() {
    private val mqttManager by lazy {
        MqttCoroutineManager(
            mqttPayloadListener = mqttPayloadListener,
            mqttConnectionStateListener = mqttConnectionStateListener,
            serverUrl = "tcp://test.mosquitto.org:1883",
            dispatcher = Dispatchers.Main,
            clientId = Settings.Secure.getString(
                this.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnStartMqtt.setOnClickListener {
            mqttManager.connect(
                arrayOf("home/+/temperature"),
                intArrayOf(0), mqttConnectOptions = MqttConnectOptions()
            )
        }
        btnStopMqtt.setOnClickListener { mqttManager.disconnect() }
    }

    private val mqttConnectionStateListener: MqttConnectionStateListener = { connectionState ->
        tvMqttConnection.text = "$connectionState"
        Log.i(MainActivity::class.java.simpleName, "Connection state is: $connectionState")
    }

    private val mqttPayloadListener: MqttPayloadListener = { mqttPayload ->
        Log.i(
            MainActivity::class.java.simpleName,
            "Mqtt payload is: ${mqttPayload.topic} ${mqttPayload.mqttMessage}"
        )
    }

}

