package com.sorinirimies.mqttcoroutines

enum class MqttConnectionState { CONNECTED, CONNECTING, DISCONNECTING, DISCONNECTED, DISCONNECTION_FAILED, CONNECTION_FAILED }