package com.nfcgate.wear.model

import org.json.JSONObject

sealed class WearMessage {
    data object StartCapture : WearMessage()
    data object StopCapture : WearMessage()
    data object PauseCapture : WearMessage()
    data object ResumeCapture : WearMessage()
    data object QueryStatus : WearMessage()
    data class SessionUpdate(val count: Int, val bytes: Long, val active: Boolean, val paused: Boolean = false) : WearMessage()
    data class TapEvent(val type: String, val data: String, val timestamp: Long) : WearMessage()

    fun toJson(): String {
        val json = JSONObject()
        when (this) {
            StartCapture -> json.put("type", "start")
            StopCapture -> json.put("type", "stop")
            PauseCapture -> json.put("type", "pause")
            ResumeCapture -> json.put("type", "resume")
            QueryStatus -> json.put("type", "query")
            is SessionUpdate -> {
                json.put("type", "session")
                json.put("count", count)
                json.put("bytes", bytes)
                json.put("active", active)
                json.put("paused", paused)
            }
            is TapEvent -> {
                json.put("type", "tap")
                json.put("tapType", type)
                json.put("data", data)
                json.put("timestamp", timestamp)
            }
        }
        return json.toString()
    }

    companion object {
        fun fromJson(raw: String): WearMessage? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            return when (json.optString("type")) {
                "start" -> StartCapture
                "stop" -> StopCapture
                "pause" -> PauseCapture
                "resume" -> ResumeCapture
                "query" -> QueryStatus
                "session" -> SessionUpdate(
                    count = json.optInt("count", 0),
                    bytes = json.optLong("bytes", 0L),
                    active = json.optBoolean("active", false),
                    paused = json.optBoolean("paused", false)
                )
                "tap" -> TapEvent(
                    type = json.optString("tapType", "unknown"),
                    data = json.optString("data", ""),
                    timestamp = json.optLong("timestamp", 0L)
                )
                else -> null
            }
        }
    }
}
