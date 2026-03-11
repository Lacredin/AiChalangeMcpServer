package com.example.aichalangemcpserver.infrastructure.tool

import com.example.aichalangemcpserver.domain.tool.McpTool
import com.example.aichalangemcpserver.domain.tool.ToolDefinition
import com.example.aichalangemcpserver.domain.tool.ToolExecutionResult
import com.example.aichalangemcpserver.infrastructure.ws.TimerWebSocketHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class TimerTool(
    private val wsHub: TimerWebSocketHub,
    private val backgroundScope: CoroutineScope
) : McpTool {
    override val definition: ToolDefinition = ToolDefinition(
        name = "timer.start",
        description = """
            Запускает таймер и отправляет уведомление по WebSocket по истечении времени.
            Инструкция для клиента:
            1) До вызова tools/call открыть WebSocket: ws://<host>:8080/ws?clientId=<ВАШ_CLIENT_ID>
            2) Убедиться, что соединение установлено (состояние OPEN)
            3) Вызвать timer.start и передать тот же clientId в arguments.clientId
            4) Ожидать событие JSON: {"event":"timer.triggered","clientId":"...","delaySeconds":N,"message":"..."}
        """.trimIndent(),
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("clientId") {
                    put("type", "string")
                    put("description", "Идентификатор клиента из query-параметра WebSocket: /ws?clientId=<clientId>.")
                }
                putJsonObject("delaySeconds") {
                    put("type", "integer")
                    put("minimum", 1)
                    put("description", "Длительность таймера в секундах.")
                }
                putJsonObject("message") {
                    put("type", "string")
                    put("description", "Текст, который придёт клиенту после срабатывания.")
                }
            }
            put("additionalProperties", false)
            put("required", kotlinx.serialization.json.buildJsonArray {
                add(JsonPrimitive("clientId"))
                add(JsonPrimitive("delaySeconds"))
            })
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val clientId = arguments["clientId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (clientId.isBlank()) {
            return ToolExecutionResult("Отсутствует обязательный аргумент: clientId", isError = true)
        }

        val delaySeconds = arguments["delaySeconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
        if (delaySeconds == null || delaySeconds <= 0) {
            return ToolExecutionResult("Аргумент delaySeconds должен быть целым числом больше 0", isError = true)
        }

        val message = arguments["message"]?.jsonPrimitive?.contentOrNull?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Таймер сработал"

        backgroundScope.launch {
            delay(delaySeconds * 1_000)
            wsHub.publish(
                clientId = clientId,
                payload = buildJsonObject {
                    put("event", "timer.triggered")
                    put("clientId", clientId)
                    put("delaySeconds", delaySeconds)
                    put("message", message)
                }
            )
        }

        return ToolExecutionResult(
            text = "Таймер запущен на $delaySeconds сек. Клиент: $clientId"
        )
    }
}
