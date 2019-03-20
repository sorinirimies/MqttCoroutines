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
internal typealias MqttConnectionStateListener = (MqttConnectionState) -> Unit

class MqttCoroutineManager(
    private val serverUri: String,
    private val clientId: String
) : MqttManager, CoroutineScope {

    private val job = Job()
    private var maxNumberOfRetries = 4
    private var retryInterval = 4000L
    private var topics = arrayOf<String>()
    private var qos = intArrayOf()
    private var timerReconnect: Timer? = null
    private var retryCount = 0
    private var isMqttClientConnected = false
    private val mqttClient by lazy(LazyThreadSafetyMode.NONE) {
        MqttAsyncClient(
            serverUri,
            clientId,
            MemoryPersistence()
        )
    }
    private var mqttConnectOptions: MqttConnectOptions? = null
    private var explicitDisconnection = false
    private val tag = MqttCoroutineManager::class.java.simpleName

    private var mqttPayload: MqttPayload? = null
    private var mqttConnectionStateListener: MqttConnectionStateListener? = null
    private val mqttPayloadChannel: SendChannel<Pair<String, MqttMessage>> =
        actor { channel.consumeEach { mqttPayload?.invoke(it) } }
    private val mqttConnectionStatusChannel: SendChannel<MqttConnectionState> =
        actor { channel.consumeEach { mqttConnectionStateListener?.invoke(it) } }
    override val coroutineContext: CoroutineContext get() = job + Dispatchers.Default

    override fun subscribeToMqttPayload(mqttPayload: MqttPayload) {
        this.mqttPayload = mqttPayload
    }

    override fun subscribeToMqttConnectionState(mqttConnectionStateListener: MqttConnectionStateListener) {
        this.mqttConnectionStateListener = mqttConnectionStateListener
    }

    override fun connect(
        topics: Array<String>,
        qos: IntArray,
        mqttConnectOptions: MqttConnectOptions?,
        retryInterval: Long,
        maxNumberOfRetries: Int
    ): Boolean {
        if (isMqttClientConnected) {
            Log.i(tag, "connect was called although the mqttClient is already connected.")
            return isMqttClientConnected
        }
        job.start()
        this@MqttCoroutineManager.topics = topics
        this@MqttCoroutineManager.maxNumberOfRetries = maxNumberOfRetries
        this@MqttCoroutineManager.retryInterval = retryInterval
        this@MqttCoroutineManager.qos = qos
        this@MqttCoroutineManager.mqttConnectOptions = mqttConnectOptions
        mqttClient.setCallback(mqttMessageCallback)
        try {
            sendMqttConnectionStatus(MqttConnectionState.CONNECTING)
            mqttClient.connect(mqttConnectOptions, null, connectAction)
        } catch (cause: MqttException) {
            isMqttClientConnected = false
            sendMqttConnectionStatus(MqttConnectionState.CONNECTION_FAILED)
            Log.e(tag, "MQTT connecting issue: $cause")
        }
        return isMqttClientConnected
    }

    private val mqttMessageCallback = object : MqttCallback {
        @Throws(Exception::class)
        override fun messageArrived(topic: String, message: MqttMessage) {
            sendMqttPayload(topic to message)
            Log.i(tag, "Mqtt payload arrived: ${message.payload.toString(Charsets.UTF_8)}")
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit

        override fun connectionLost(cause: Throwable) {
            sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
            Log.d(cause.message, "MQTT connection lost!")
            isMqttClientConnected = false
            if (explicitDisconnection) {
                job.cancel()
                return
            }
            resetTimer()
            retryConnection()
        }
    }

    fun sendMqttPayload(message: Pair<String, MqttMessage>) = launch {
        mqttPayloadChannel.send(message)
    }

    private fun sendMqttConnectionStatus(mqttConnectionState: MqttConnectionState) = launch {
        mqttConnectionStatusChannel.send(mqttConnectionState)
    }

    private val connectAction = object : IMqttActionListener {

        val mqttSubscriptionResult = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(tag, "MQTT subscription successful")
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                Log.e(tag, "MQTT could not subscribe: $exception")
            }
        }

        override fun onSuccess(asyncActionToken: IMqttToken?) {
            isMqttClientConnected = true
            sendMqttConnectionStatus(MqttConnectionState.CONNECTED)
            Log.d(MqttCoroutineManager::class.java.simpleName, "MQTT connected")
            mqttClient.subscribe(topics, qos, null, mqttSubscriptionResult)
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
            isMqttClientConnected = false
            sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
            Log.e(tag, "MQTT could not establish connection: $exception")
            if (!explicitDisconnection) {
                retryConnection()
            }
        }
    }

    private val disconnectAction = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            Log.i(tag, "Mqtt Client disconnected")
            sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
            isMqttClientConnected = false
            explicitDisconnection = true
            job.cancel()
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
            Log.e(tag, "Disconnection failed: $exception")
            sendMqttConnectionStatus(MqttConnectionState.DISCONNECTION_FAILED)
        }
    }

    override fun disconnect() {
        if (!isMqttClientConnected) {
            Log.w(tag, "disconnect was called although the mqttClient is not connected")
            return
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

    private fun resetTimer() {
        retryCount = 0
        timerReconnect?.apply {
            cancel()
            purge()
        }
        timerReconnect = null
    }

    private fun retryConnection() {
        if (timerReconnect == null) {
            timerReconnect = fixedRateTimer("mqtt_reconnect_timer", true, 0, retryInterval) {
                retryCount++
                Log.i(tag, "MQTT reconnection retry count: $retryCount")
                if (mqttClient.isConnected || retryCount > maxNumberOfRetries) {
                    sendMqttConnectionStatus(MqttConnectionState.CONNECTED)
                    cancel()
                }
                connect(topics, qos, mqttConnectOptions)
            }
        }
    }
}