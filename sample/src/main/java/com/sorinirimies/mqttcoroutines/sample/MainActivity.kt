package com.sorinirimies.mqttcoroutines.sample

import android.annotation.SuppressLint
import android.os.Bundle
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.sorinirimies.mqttcoroutines.MqttProviderConfiguration
import com.sorinirimies.mqttcoroutines.MqttProviderImpl
import com.sorinirimies.mqttcoroutines.MqttState
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
            launch {
                mqttProvider.connect(
                    arrayOf(topic),
                    intArrayOf(0), mqttConnectOptions = MqttConnectOptions()
                ).collect { mqttState ->
                    when (mqttState) {
                        is MqttState.MqttConnection -> tvMqttConnection.text =
                            "${mqttState.mqttConnectionState}"

                        is MqttState.MqttPayload -> {
                            Toast.makeText(
                                this@MainActivity,
                                "Mqtt payload for topic ${mqttState.topic} is ${mqttState.msg.toString(
                                    Charsets.UTF_8
                                )}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
        btnStopMqtt.setOnClickListener { mqttProvider.disconnect() }
        btnSendMessage.setOnClickListener {
            launch {
                mqttProvider.sendPayload(
                    MqttState.MqttPayload(topic, edtMessage.text.toString().toByteArray(), 0)
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}

