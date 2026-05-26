package com.nfcgate.hce.relay

object RelayManager {
    @Volatile
    var activeClient: RelayClient? = null
        private set

    @Volatile
    var currentServer: String = "ws://10.0.2.2:8080/ws/relay"
        private set

    @Volatile
    var currentToken: String = ""
        private set

    fun startRelay(token: String, server: String, role: String): RelayClient {
        stopRelay("restart")
        currentServer = server
        currentToken = token
        val client = RelayClient(server, token, role)
        activeClient = client
        client.connect()
        return client
    }

    fun stopRelay(reason: String = "stop") {
        activeClient?.disconnect(reason)
        activeClient = null
    }
}
