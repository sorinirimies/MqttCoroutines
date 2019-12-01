package com.sorinirimies.mqttcoroutines.sample

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.sorinirimies.mqttcoroutines.MqttPayload
import com.sorinirimies.mqttcoroutines.MqttProviderConfiguration
import com.sorinirimies.mqttcoroutines.MqttProviderImpl
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

@SuppressLint("HardwareIds")
@ExperimentalCoroutinesApi
@FlowPreview
class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val topic = "home/temperature"
    private val mqttProvider by lazy {
        MqttProviderImpl(
            mqttProviderConfiguration = MqttProviderConfiguration(
                serverUrl = "tcp://test.mosquitto.org:1883",
                clientId = Settings.Secure.getString(
                    this.contentResolver,
                    Settings.Secure.ANDROID_ID
                )
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnStartMqtt.setOnClickListener {
            mqttProvider.connect(
                arrayOf(topic),
                intArrayOf(0), mqttConnectOptions = MqttConnectOptions()
            )
        }
        btnStopMqtt.setOnClickListener { mqttProvider.disconnect() }
        btnSendMessage.setOnClickListener {
            mqttProvider.sendPayload(
                MqttPayload(topic, edtMessage.text.toString().toByteArray(), 0)
            )
        }
        launch {
            mqttProvider.mqttConnectionStateFlow.collect { conState ->
                tvMqttConnection.text = "$conState"
                Log.i(
                    MainActivity::class.java.simpleName,
                    "Connection state is: $conState"
                )
            }
        }
        launch {
            mqttProvider.mqttPayloadFlow.collect { payload ->
                Log.i(
                    MainActivity::class.java.simpleName,
                    "Mqtt payload is: ${payload.topic} ${payload.msg.toString(Charsets.UTF_8)}"
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}

