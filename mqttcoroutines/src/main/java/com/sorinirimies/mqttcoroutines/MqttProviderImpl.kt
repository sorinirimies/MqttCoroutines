package com.sorinirimies.mqttcoroutines

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.flow.callbackFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.MqttException

sealed class MqttState {
    data class MqttPayload(
        val topic: String,
        val msg: ByteArray,
        val qos: Int,
        val retained: Boolean = false
    ) : MqttState()

    data class MqttConnection(val mqttConnectionState: MqttConnectionState) : MqttState()
}

@ExperimentalCoroutinesApi
@FlowPreview
class MqttProviderImpl(
    private val mqttProviderConfiguration: MqttProviderConfiguration
) : MqttProvider {
    private var maxNumberOfRetries = 4
    private var retryInterval = 4000L
    private var topics = arrayOf<String>()
    private var qos = intArrayOf()
    private var retryCount = 0
    private var timerReconnect = ticker(0, retryInterval).apply {
        retryCount++
        Log.i(tag, "MQTT reconnection retry count: $retryCount")
        if (mqttClient.isConnected || retryCount > maxNumberOfRetries) {
            (MqttConnectionState.CONNECTED)
        }
        connect(topics, qos, mqttConnectOptions)
    }
    private var isMqttClientConnected = false
    private val mqttClient by lazy(LazyThreadSafetyMode.NONE) {
        MqttAsyncClient(
            mqttProviderConfiguration.serverUrl,
            mqttProviderConfiguration.clientId,
            mqttProviderConfiguration.mqttClientPersistence
        )
    }
    private var mqttConnectOptions: MqttConnectOptions? = null
    private var explicitDisconnection = false
    private val tag = MqttProviderImpl::class.java.simpleName

    override suspend fun sendPayload(mqtt: MqttState.MqttPayload) {
        try {
            mqttClient.publish(mqtt.topic, MqttMessage(mqtt.msg))
            Log.i(tag, "client disconnected")
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
    ) = callbackFlow {
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
                if (!explicitDisconnection) {
                    retryConnection()
                    return
                }
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
                    isMqttClientConnected = false
                    if (explicitDisconnection) return
                    retryConnection()
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
    }

    private fun retryConnection() {

    }

    override fun disconnect() = callbackFlow {
        if (!isMqttClientConnected) {
            Log.w(tag, "disconnect was called although the mqttClient is not connected")
            return@callbackFlow
        }
        val disconnectAction = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                isMqttClientConnected = false
                explicitDisconnection = true
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
                explicitDisconnection = true
                Log.i(tag, "Client is already disconnected!")
            } else {
                Log.e(tag, "Disconnection error: $cause")
            }
        }
    }
}
