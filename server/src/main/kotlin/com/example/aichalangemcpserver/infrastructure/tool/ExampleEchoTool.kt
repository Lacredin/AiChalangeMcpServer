package com.example.aichalangemcpserver.infrastructure.tool

import com.example.aichalangemcpserver.domain.tool.McpTool
import com.example.aichalangemcpserver.domain.tool.ToolDefinition
import com.example.aichalangemcpserver.domain.tool.ToolExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ExampleEchoTool : McpTool {
    override val definition: ToolDefinition = ToolDefinition(
        name = "example.echo",
        description = "Возвращает переданное сообщение. Может перевести ответ в верхний регистр.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("message") {
                    put("type", "string")
                    put("description", "Сообщение, которое нужно вернуть.")
                }
                putJsonObject("uppercase") {
                    put("type", "boolean")
                    put("description", "Если true, вернуть сообщение в верхнем регистре.")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("message"))
            }
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val message = arguments["message"]?.jsonPrimitive?.contentOrNull
        if (message.isNullOrBlank()) {
            return ToolExecutionResult(
                text = "Отсутствует обязательный аргумент: message",
                isError = true
            )
        }

        val uppercase = arguments["uppercase"]?.jsonPrimitive?.booleanOrNull ?: false
        val output = if (uppercase) message.uppercase() else message
        return ToolExecutionResult(text = output)
    }
}
