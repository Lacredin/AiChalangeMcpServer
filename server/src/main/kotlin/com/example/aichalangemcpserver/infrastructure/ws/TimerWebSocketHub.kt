package com.example.aichalangemcpserver.infrastructure.ws

import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class TimerWebSocketHub(
    private val json: Json = Json
) {
    private val sessionsByClientId = ConcurrentHashMap<String, CopyOnWriteArraySet<WebSocketServerSession>>()

    suspend fun register(clientId: String, session: WebSocketServerSession) {
        sessionsByClientId.computeIfAbsent(clientId) { CopyOnWriteArraySet() }.add(session)
    }

    suspend fun unregister(clientId: String, session: WebSocketServerSession) {
        sessionsByClientId[clientId]?.remove(session)
        if (sessionsByClientId[clientId].isNullOrEmpty()) {
            sessionsByClientId.remove(clientId)
        }
    }

    suspend fun publish(clientId: String, payload: JsonObject): Boolean {
        val sessions = sessionsByClientId[clientId] ?: return false
        if (sessions.isEmpty()) {
            return false
        }

        val message = json.encodeToString(JsonObject.serializer(), payload)
        var delivered = false

        sessions.forEach { session ->
            runCatching {
                session.send(Frame.Text(message))
                delivered = true
            }.onFailure {
                sessions.remove(session)
            }
        }

        if (sessions.isEmpty()) {
            sessionsByClientId.remove(clientId)
        }

        return delivered
    }

    suspend fun closeAll() {
        sessionsByClientId.values.forEach { sessions ->
            sessions.forEach { session ->
                runCatching {
                    session.close()
                }
            }
        }
        sessionsByClientId.clear()
    }
}
