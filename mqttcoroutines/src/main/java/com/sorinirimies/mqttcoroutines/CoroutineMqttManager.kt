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

typealias MqttPayload = (Pair<String, MqttMessage>) -> Unit
typealias MqttConnectionStatus = (Boolean) -> Unit

class CoroutineMqttManager(mqttPayload: MqttPayload, mqttConnectionStatus: MqttConnectionStatus) : MqttManager,
    CoroutineScope {
    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Default
    private var maxNumberOfRetries = 4
    private var retryInterval = 4000L
    private var topics: Array<String> = arrayOf()
    private var qos: IntArray = intArrayOf()
    private var timerReconnect: Timer? = null
    private var retryCount = 0
    private var isMqttClientConnected = false
    private var mqttClient: MqttAsyncClient? = null
    private var user: String? = null
    private var clientId: String? = null
    private var explicitDisconnection = false
    private val TAG = CoroutineMqttManager::class.java.simpleName

    private val channelMqttPayload: SendChannel<Pair<String, MqttMessage>> = actor {
        channel.consumeEach {
            mqttPayload.invoke(it)
        }
    }

    private val channelMqttConnectionState: SendChannel<Boolean> = actor {
        channel.consumeEach {
            mqttConnectionStatus.invoke(it)
        }
    }

    override fun connect(
        serverURI: String,
        topics: Array<String>,
        qos: IntArray,
        clientId: String?,
        user: String?
    ) {
        if (isMqttClientConnected) {
            Log.i(TAG, "connect was called although the mqttClient is already connected")
            return
        }
        job.start()
        this@CoroutineMqttManager.topics = topics
        this@CoroutineMqttManager.qos = qos
        this@CoroutineMqttManager.clientId = clientId
        this@CoroutineMqttManager.user = user
        mqttClient = MqttAsyncClient(serverURI, clientId, MemoryPersistence())
        mqttClient?.setCallback(object : MqttCallback {
            @Throws(Exception::class)
            override fun messageArrived(topic: String, message: MqttMessage) {
                val msg = message.payload.toString(Charsets.UTF_8)
                Log.i(TAG, "Mqtt payload arrived: $msg")
                sendMqttPayload(topic to message)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit

            override fun connectionLost(cause: Throwable) {
                isMqttClientConnected = false
                sendMqttConnectionStatus(false)
                channelMqttConnectionState.close()
                Log.d(cause.message, "MQTT connection lost!")
                if (!explicitDisconnection) {
                    resetTimer()
                    retry()
                }
            }
        })
        val connectAction: IMqttActionListener = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                isMqttClientConnected = true
                sendMqttConnectionStatus(true)
                Log.d(CoroutineMqttManager::class.java.simpleName, "MQTT connected")
                mqttClient?.subscribe(topics, qos, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.i(TAG, "MQTT subscription successful")
                    }

                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                        Log.e(TAG, "MQTT could not subscribe: $exception")
                    }
                })
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                isMqttClientConnected = false
                channelMqttPayload.close()
                channelMqttConnectionState.close()
                Log.e(TAG, "MQTT could not establish connection: $exception")
                if (!explicitDisconnection) {
                    retry()
                }
            }
        }
        try {
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                userName = user
            }
            mqttClient?.connect(options, null, connectAction)
        } catch (cause: MqttException) {
            Log.e(TAG, "MQTT connecting issue: $cause")
        }
    }

    fun sendMqttConnectionStatus(isConnected: Boolean = false) = launch {
        channelMqttConnectionState.send(isConnected)
    }

    fun sendMqttPayload(message: Pair<String, MqttMessage>) = launch {
        channelMqttPayload
            .send(message)
    }

    override fun disconnect() {
        if (!isMqttClientConnected) {
            Log.w(TAG, "disconnect was called although the mqttClient is not connected")
            return
        }
        val disconnectAction: IMqttActionListener = object : IMqttActionListener {
            override fun onSuccess(asyncActionToken: IMqttToken?) {
                Log.i(TAG, "Mqtt Client disconnected")
                isMqttClientConnected = false
                explicitDisconnection = true
                channelMqttPayload.close()
                channelMqttConnectionState.close()
                job.cancel()
            }

            override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable) {
                Log.e(TAG, "Disconnection failed: $exception")
            }
        }
        try {
            if (mqttClient?.isConnected == true)
                mqttClient?.disconnect(null, disconnectAction)
        } catch (cause: MqttException) {
            if (cause.reasonCode == MqttException.REASON_CODE_CLIENT_ALREADY_DISCONNECTED.toInt()) {
                isMqttClientConnected = false
                explicitDisconnection = true
                Log.i(TAG, "Client is already disconnected!")
            } else {
                Log.e(TAG, "Disconnection error: $cause")
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
                Log.i(TAG, "MQTT reconnection retry: $retryCount")
                if (mqttClient?.isConnected == true || retryCount > maxNumberOfRetries) {
                    sendMqttConnectionStatus(isMqttClientConnected)
                    cancel()
                }
                val loggedIn = user != null
                if (loggedIn) connect(mqttClient?.serverURI ?: "", topics, qos, clientId, user)
                else disconnect()
            }
        }
    }
}