package com.sorinirimies.mqttcoroutines

import org.eclipse.paho.client.mqttv3.MqttClientPersistence
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

data class MqttProviderConfiguration(
    val mqttClientPersistence: MqttClientPersistence = MemoryPersistence(),
    val serverUrl: String = "",
    val clientId: String = ""
)