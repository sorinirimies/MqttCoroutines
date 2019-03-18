package com.sorinirimies.mqttcoroutines

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.coroutines.CoroutineContext

internal typealias MqttPayload = (Pair<String, MqttMessage>) -> Unit
internal typealias MqttConnectionStatus = (MqttConnectionState) -> Unit

class CoroutineMqttManager(
    mqttPayload: MqttPayload,
    mqttConnectionStatus: MqttConnectionStatus,
    private val serverUri: String,
    private val clientId: String,
    private var user: String?
) : MqttManager,
    CoroutineScope {
    private val job = Job()
    private var maxNumberOfRetries = 4
    private var retryInterval = 4000L
    private var topics: Array<String> = arrayOf()
    private var qos: IntArray = intArrayOf()
    private var timerReconnect: Timer? = null
    private var retryCount = 0
    private var isMqttClientConnected = false
    private val mqttClient: MqttAsyncClient by lazy { MqttAsyncClient(serverUri, clientId, MemoryPersistence()) }
    private var mqttConnectOptions: MqttConnectOptions? = null
    private var explicitDisconnection = false
    private val tag = CoroutineMqttManager::class.java.simpleName

    private val channelMqttPayload: SendChannel<Pair<String, MqttMessage>> =
        actor { channel.consumeEach { mqttPayload.invoke(it) } }

    private val channelMqttConnectionState: SendChannel<MqttConnectionState> =
        actor { channel.consumeEach { mqttConnectionStatus.invoke(it) } }

    override val coroutineContext: CoroutineContext get() = job + Dispatchers.Default

    override fun connect(
        serverURI: String,
        topics: Array<String>,
        qos: IntArray,
        mqttConnectOptions: MqttConnectOptions?
    ) {
        if (isMqttClientConnected) {
            Log.i(tag, "connect was called although the mqttClient is already connected.")
            return
        }
        job.start()
        this@CoroutineMqttManager.topics = topics
        this@CoroutineMqttManager.qos = qos
        this@CoroutineMqttManager.mqttConnectOptions = mqttConnectOptions
        this@CoroutineMqttManager.user = user
        mqttClient.setCallback(object : MqttCallback {
            @Throws(Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
                Log.i(tag, "Mqtt payload arrived: ${message.payload.toString(Charsets.UTF_8)}")
                sendMqttPayload(topic to message)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit

            override fun connectionLost(cause: Throwable) {
                sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
                Log.d(cause.message, "MQTT connection lost!")
                isMqttClientConnected = false
                if (explicitDisconnection) {
                    channelMqttConnectionState.close()
                    channelMqttPayload.close()
                    job.cancel()
                    return
                }
                resetTimer()
                retry()
            }
        })
        val connectAction: IMqttActionListener = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                isMqttClientConnected = true
                sendMqttConnectionStatus(MqttConnectionState.CONNECTED)
                Log.d(CoroutineMqttManager::class.java.simpleName, "MQTT connected")
                mqttClient.subscribe(topics, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(tag, "MQTT subscription successful")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                        Log.e(tag, "MQTT could not subscribe: $exception")
                    }
                })
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                isMqttClientConnected = false
                sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
                channelMqttPayload.close()
                channelMqttConnectionState.close()
                Log.e(tag, "MQTT could not establish connection: $exception")
                if (!explicitDisconnection) {
                    retry()
                }
            }
        }
        try {
            mqttClient.connect(mqttConnectOptions, null, connectAction)
            sendMqttConnectionStatus(MqttConnectionState.CONNECTING)
        } catch (cause: MqttException) {
            sendMqttConnectionStatus(MqttConnectionState.CONNECTION_FAILED)
            Log.e(tag, "MQTT connecting issue: $cause")
        }
    }

    fun sendMqttConnectionStatus(mqttConnectionState: MqttConnectionState) = launch {
        channelMqttConnectionState.send(mqttConnectionState)
    }

    fun sendMqttPayload(message: Pair<String, MqttMessage>) = launch {
        channelMqttPayload
            .send(message)
    }

    override fun disconnect() {
        if (!isMqttClientConnected) {
            Log.w(tag, "disconnect was called although the mqttClient is not connected")
            return
        }
        val disconnectAction: IMqttActionListener = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(tag, "Mqtt Client disconnected")
                sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
                isMqttClientConnected = false
                explicitDisconnection = true
                channelMqttPayload.close()
                channelMqttConnectionState.close()
                job.cancel()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                Log.e(tag, "Disconnection failed: $exception")
                sendMqttConnectionStatus(MqttConnectionState.DISCONNECTION_FAILED)
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

    override fun setRetryIntervalTime(retryInterval: Long) {
        this.retryInterval = retryInterval
    }

    override fun setMaxNumberOfRetires(maxNumberOfRetries: Int) {
        this.maxNumberOfRetries = maxNumberOfRetries
    }

    private fun resetTimer() {
        retryCount = 0
        timerReconnect?.let {
            it.cancel()
            it.purge()
        }
        timerReconnect = null
    }

    private fun retry() {
        if (timerReconnect == null) {
            timerReconnect = fixedRateTimer("mqtt_reconnect_timer", true, 0, retryInterval) {
                retryCount++
                Log.i(tag, "MQTT reconnection retry: $retryCount")
                if (mqttClient.isConnected || retryCount > maxNumberOfRetries) {
                    sendMqttConnectionStatus(MqttConnectionState.CONNECTED)
                    cancel()
                }
                val loggedIn = user != null
                if (loggedIn) connect(serverUri, topics, qos, mqttConnectOptions)
                else disconnect()
            }
        }
    }
}