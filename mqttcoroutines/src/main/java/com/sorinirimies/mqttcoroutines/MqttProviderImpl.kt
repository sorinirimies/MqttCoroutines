package com.sorinirimies.mqttcoroutines

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.MqttException

@ExperimentalCoroutinesApi
 internal class MqttProviderImpl(
    private val mqttProviderConfiguration: MqttProviderConfiguration
) : MqttProvider {
    private var isMqttClientConnected = false
    private val mqttClient by lazy(LazyThreadSafetyMode.NONE) {
        MqttAsyncClient(
            mqttProviderConfiguration.serverUrl,
            mqttProviderConfiguration.clientId,
            mqttProviderConfiguration.mqttClientPersistence
        )
    }
    private val tag = MqttProviderImpl::class.java.simpleName

    override suspend fun sendPayload(mqtt: MqttState.MqttPayload) {
        try {
            mqttClient.publish(mqtt.topic, MqttMessage(mqtt.msg))
        } catch (e: MqttPersistenceException) {
            e.printStackTrace()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    override fun connect(
        topics: Array<String>,
        qos: IntArray,
        mqttConnectOptions: MqttConnectOptions?,
        retryInterval: Long,
        maxNumberOfRetries: Int
    ) = callbackFlow<MqttState> {
        if (isMqttClientConnected) {
            Log.i(tag, "connect was called although the mqttClient is already connected.")
            return@callbackFlow
        }
        val connectActionListener = object : IMqttActionListener {
            val subscriptionResultListener = object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.i(tag, "MQTT subscription successful")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                    Log.e(tag, "MQTT could not subscribe: $exception")
                }
            }

            override fun onSuccess(asyncActionToken: IMqttToken?) {
                offer(MqttState.MqttConnection(MqttConnectionState.CONNECTED))
                isMqttClientConnected = true
                mqttClient.setCallback(mqttMessageCallback)
                mqttClient.subscribe(topics, qos, null, subscriptionResultListener)
                Log.d(MqttProviderImpl::class.java.simpleName, "MQTT connected")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                Log.e(tag, "MQTT could not establish connection: $exception")
                isMqttClientConnected = false
                offer(MqttState.MqttConnection(MqttConnectionState.DISCONNECTED))
            }

            val mqttMessageCallback = object : MqttCallback {
                @Throws(Exception::class)
                override fun messageArrived(topic: String, message: MqttMessage) {
                    offer(
                        MqttState.MqttPayload(
                            topic,
                            message.payload,
                            message.qos
                        )
                    )
                    Log.i(tag, "Mqtt payload arrived: ${message.payload.toString(Charsets.UTF_8)}")
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit

                override fun connectionLost(cause: Throwable) {
                    offer(MqttState.MqttConnection(MqttConnectionState.DISCONNECTED))
                    Log.d(cause.message, "MQTT connection lost!")
                }
            }
        }
        try {
            offer(MqttState.MqttConnection(MqttConnectionState.CONNECTING))
            mqttClient.connect(mqttConnectOptions, null, connectActionListener)
        } catch (e: Exception) {
            isMqttClientConnected = false
            offer(MqttState.MqttConnection(MqttConnectionState.CONNECTION_FAILED))
            when (e) {
                is MqttException -> Log.e(tag, "MQTT connection issue: $e")
                is IllegalArgumentException -> Log.e(tag, "MQTT illegal argument exception: $e")
                else -> Log.d(tag, "Mqtt connection exception: $e")
            }
        }
        awaitClose {
            mqttClient.disconnect()
        }
    }

    override fun disconnect() = callbackFlow<MqttState> {
        if (!isMqttClientConnected) {
            Log.w(tag, "disconnect was called although the mqttClient is not connected")
            return@callbackFlow
        }
        val disconnectAction = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                isMqttClientConnected = false
                offer(MqttState.MqttConnection(MqttConnectionState.DISCONNECTED))
                Log.i(tag, "Mqtt Client disconnected")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                offer(MqttState.MqttConnection(MqttConnectionState.DISCONNECTION_FAILED))
                Log.e(tag, "Disconnection failed: $exception")
            }
        }

        try {
            if (mqttClient.isConnected)
                mqttClient.disconnect(null, disconnectAction)
        } catch (cause: MqttException) {
            if (cause.reasonCode == MqttException.REASON_CODE_CLIENT_ALREADY_DISCONNECTED.toInt()) {
                isMqttClientConnected = false
                Log.i(tag, "Client is already disconnected!")
            } else {
                Log.e(tag, "Disconnection error: $cause")
            }
        }
    }
}
