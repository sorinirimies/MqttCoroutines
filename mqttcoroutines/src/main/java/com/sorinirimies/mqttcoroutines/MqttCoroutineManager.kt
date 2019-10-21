package com.sorinirimies.mqttcoroutines

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.*
import kotlin.concurrent.fixedRateTimer
import kotlin.coroutines.CoroutineContext

data class MqttPayload(val topic: String, val mqttMessage: MqttMessage)
typealias MqttPayloadListener = (MqttPayload) -> Unit
typealias MqttConnectionStateListener = (MqttConnectionState) -> Unit

@ExperimentalCoroutinesApi
@FlowPreview
class MqttCoroutineManager(
    private val mqttPayloadListener: MqttPayloadListener,
    private val mqttConnectionStateListener: MqttConnectionStateListener,
    private val memoryPersistence: MqttClientPersistence = MemoryPersistence(),
    private var dispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val serverUrl: String,
    private val clientId: String
) : MqttManager, CoroutineScope {

    private var job = Job()
    private var maxNumberOfRetries = 4
    private var retryInterval = 4000L
    private var topics = arrayOf<String>()
    private var qos = intArrayOf()
    private var timerReconnect: Timer? = null
    private var retryCount = 0
    private var isMqttClientConnected = false
    private val mqttClient by lazy(LazyThreadSafetyMode.NONE) {
        MqttAsyncClient(
            serverUrl,
            clientId,
            memoryPersistence
        )
    }
    private var mqttConnectOptions: MqttConnectOptions? = null
    private var explicitDisconnection = false
    private val tag = MqttCoroutineManager::class.java.simpleName
    private var mqttConnectionChannel = ConflatedBroadcastChannel<MqttConnectionState>()
    private var mqttPayloadChannel = ConflatedBroadcastChannel<MqttPayload>()
    override val coroutineContext: CoroutineContext get() = job + dispatcher

    override fun connect(
        topics: Array<String>,
        qos: IntArray,
        mqttConnectOptions: MqttConnectOptions?,
        retryInterval: Long,
        maxNumberOfRetries: Int
    ) {
        if (isMqttClientConnected) {
            Log.i(tag, "connect was called although the mqttClient is already connected.")
            return
        }

        this.topics = topics
        this.maxNumberOfRetries = maxNumberOfRetries
        this.retryInterval = retryInterval
        this.qos = qos
        this.mqttConnectOptions = mqttConnectOptions

        job = Job()
        mqttConnectionChannel = ConflatedBroadcastChannel()
        mqttPayloadChannel = ConflatedBroadcastChannel()
        launch {
            mqttConnectionChannel.asFlow().collect {
                mqttConnectionStateListener.invoke(it)
            }
            mqttPayloadChannel.asFlow().collect {
                mqttPayloadListener.invoke(it)
            }
        }
        job.start()

        try {
            sendMqttConnectionStatus(MqttConnectionState.CONNECTING)
            mqttClient.connect(mqttConnectOptions, null, connectActionListener)
        } catch (e: Exception) {
            isMqttClientConnected = false
            sendMqttConnectionStatus(MqttConnectionState.CONNECTION_FAILED)
            when (e) {
                is MqttException -> Log.e(tag, "MQTT connection issue: $e")
                is IllegalArgumentException -> Log.e(tag, "MQTT illegal argument exception: $e")
            }
        }
    }

    private val mqttMessageCallback = object : MqttCallback {
        @Throws(Exception::class)
        override fun messageArrived(topic: String, message: MqttMessage) {
            sendMqttPayload(MqttPayload(topic, message))
            Log.i(tag, "Mqtt payload arrived: ${message.payload.toString(Charsets.UTF_8)}")
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit

        override fun connectionLost(cause: Throwable) {
            sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
            Log.d(cause.message, "MQTT connection lost!")
            isMqttClientConnected = false
            if (explicitDisconnection) return
            resetTimer()
            retryConnection()
        }
    }

    private fun sendMqttPayload(message: MqttPayload) =
        launch { mqttPayloadChannel.send(message) }

    private fun sendMqttConnectionStatus(mqttConnectionState: MqttConnectionState) =
        launch { mqttConnectionChannel.send(mqttConnectionState) }

    private val connectActionListener = object : IMqttActionListener {

        val subscriptionResultListener = object : IMqttActionListener {
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
            mqttClient.setCallback(mqttMessageCallback)
            mqttClient.subscribe(topics, qos, null, subscriptionResultListener)
            Log.d(MqttCoroutineManager::class.java.simpleName, "MQTT connected")
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
            isMqttClientConnected = false
            sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
            launch { job.cancelAndJoin() }
            if (!explicitDisconnection) {
                retryConnection()
            }
            Log.e(tag, "MQTT could not establish connection: $exception")
        }
    }

    private val disconnectAction = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            isMqttClientConnected = false
            explicitDisconnection = true
            sendMqttConnectionStatus(MqttConnectionState.DISCONNECTED)
            Log.i(tag, "Mqtt Client disconnected")
            launch { job.cancelAndJoin() }
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
            sendMqttConnectionStatus(MqttConnectionState.DISCONNECTION_FAILED)
            Log.e(tag, "Disconnection failed: $exception")
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
            timerReconnect = fixedRateTimer(
                "mqtt_reconnect_timer",
                true,
                0,
                retryInterval
            ) {
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