package com.sorinirimies.mqttcoroutines

import org.eclipse.paho.client.mqttv3.MqttConnectOptions

interface MqttManager {

    /**
     * Connects the mqtt client to the mqttBroker and subscribes to the given [topics]. For [qos], please refer
     * to the documentation of the paho-mqtt client
     * (https://www.eclipse.org/paho/files/mqttdoc/MQTTClient/html/qos.html)
     * @param topics
     * @param qos
     * @param mqttConnectOptions the [MqttConnectOptions] with which we can configure the client
     * @param retryInterval how often to retry connecting
     * @param maxNumberOfRetries total amount of trtries
     * @return whether mqttClient is connected or not
     */
    fun connect(
        topics: Array<String>,
        qos: IntArray,
        mqttConnectOptions: MqttConnectOptions? = null,
        retryInterval: Long = 4000L,
        maxNumberOfRetries: Int = 4
    )

    /**
     * Disconnects the mqtt client
     */
    fun disconnect()

    /**Close down completely the mqtt client*/
    fun destroyMqttClient()
}
