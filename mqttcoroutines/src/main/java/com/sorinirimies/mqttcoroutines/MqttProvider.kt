package com.sorinirimies.mqttcoroutines

import kotlinx.coroutines.flow.Flow
import org.eclipse.paho.client.mqttv3.MqttConnectOptions

interface MqttProvider {

    /**
     * Connects the mqtt client to the mqttBroker and subscribes to the given [topics]. For [qos], please refer
     * to the documentation of the paho-mqtt client
     * (https://www.eclipse.org/paho/files/mqttdoc/MQTTClient/html/qos.html)
     * @param topics
     * @param qos
     * @param mqttConnectOptions the [MqttConnectOptions] with which we can configure the client
     * @param retryInterval how often to retry connecting
     * @param maxNumberOfRetries total amount of retries
     */
    fun connect(
        topics: Array<String>,
        qos: IntArray,
        mqttConnectOptions: MqttConnectOptions? = null,
        retryInterval: Long = 4000L,
        maxNumberOfRetries: Int = 4
    )

    /**
     * [MqttPayload] [Flow] to which a consumer can subscribe
     */
    val mqttPayloadFlow: Flow<MqttPayload>

    /**
     * [MqttConnectionState] [Flow] to which a consumer can subscribe
     */
    val mqttConnectionStateFlow: Flow<MqttConnectionState>

    /** Sends a given [MqttPayload] to a broker*/
    fun sendPayload(mqtt: MqttPayload)

    /**
     * Disconnects the mqtt client
     */
    fun disconnect()
}
