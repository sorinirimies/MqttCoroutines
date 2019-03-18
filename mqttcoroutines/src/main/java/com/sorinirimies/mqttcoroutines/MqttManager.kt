package com.sorinirimies.mqttcoroutines

import org.eclipse.paho.client.mqttv3.MqttConnectOptions

interface MqttManager {

    /**
     * Connects the mqtt client to the given [serverURI] and subscribes to the given [topics]. For [qos], please refer
     * to the documentation of the paho-mqtt client
     * (https://www.eclipse.org/paho/files/mqttdoc/MQTTClient/html/qos.html)
     * @param topics
     * @param qos
     */
    fun connect(
        topics: Array<String>,
        qos: IntArray,
        mqttConnectOptions: MqttConnectOptions?,
        retryInterval : Long= 4000L,
        maxNumberOfRetries: Int = 4)

    /**
     * Disconnects the mqtt client
     */
    fun disconnect()

    /**
     * Sets the [retryInterval] as interval time for the retry mechanism in milliseconds. In case of
     * a connection loss, the mqtt client tries to reconnect every [retryInterval] milliseconds.
     * If this is not set, the default value is 4000 milliseconds.
     */
    fun setRetryIntervalTime(retryInterval: Long)

    /**
     * Sets the [maxNumberOfRetries] for the mqtt client. In case of a connection loss, the mqtt
     * client tries to reconnect a maximum number of [maxNumberOfRetries] times.
     * If this is not set, the default value is a maximum of 4 retries.
     */
    fun setMaxNumberOfRetires(maxNumberOfRetries: Int)
}
