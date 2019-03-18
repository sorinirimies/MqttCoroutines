package com.sorinirimies.mqttcoroutines

interface MqttManager {

    /**
     * Connects the mqtt client to the given [serverURI] and subscribes to the given [topics]. For [qos], please refer
     * to the documentation of the paho-mqtt client
     * (https://www.eclipse.org/paho/files/mqttdoc/MQTTClient/html/qos.html)
     * @param serverURI
     * @param topics
     * @param qos
     * @param clientId
     * @param user
     */
    fun connect(serverURI: String, topics: Array<String>, qos: IntArray, clientId: String?= null, user: String? = null)

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
