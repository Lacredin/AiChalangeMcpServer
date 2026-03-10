package com.example.aichalangemcpserver.presentation

import com.example.aichalangemcpserver.Greeting
import com.example.aichalangemcpserver.application.ToolRegistryService
import com.example.aichalangemcpserver.presentation.dto.JsonRpcErrorDto
import com.example.aichalangemcpserver.presentation.dto.JsonRpcRequestDto
import com.example.aichalangemcpserver.presentation.dto.JsonRpcResponseDto
import com.example.aichalangemcpserver.presentation.dto.ToolDto
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class Routes(
    private val toolRegistryService: ToolRegistryService
) {
    fun register(route: Routing) {
        route.get("/") {
            call.respondText(
                text = HtmlPageRenderer.page(),
                contentType = ContentType.Text.Html
            )
        }

        route.get("/api/tools") {
            call.respond(toolRegistryService.listToolDefinitions().map { tool ->
                ToolDto(
                    name = tool.name,
                    description = tool.description,
                    inputSchema = tool.inputSchema
                )
            })
        }

        route.get("/health") {
            call.respondText("OK: ${Greeting().greet()}")
        }

        route.post("/mcp") {
            val request = call.receive<JsonRpcRequestDto>()

            if (request.id == null && request.method.startsWith("notifications/")) {
                call.respond(HttpStatusCode.NoContent)
                return@post
            }

            val response = handleRpc(request)
            val status = if (response.error == null) HttpStatusCode.OK else HttpStatusCode.BadRequest
            call.respond(status, response)
        }
    }

    private suspend fun handleRpc(request: JsonRpcRequestDto): JsonRpcResponseDto {
        if (request.jsonrpc != "2.0") {
            return JsonRpcResponseDto(
                id = request.id,
                error = JsonRpcErrorDto(code = -32600, message = "Invalid Request: jsonrpc must be 2.0")
            )
        }

        return when (request.method) {
            "initialize" -> JsonRpcResponseDto(
                id = request.id,
                result = buildJsonObject {
                    put("protocolVersion", "2024-11-05")
                    putJsonObject("capabilities") {
                        putJsonObject("tools") {
                            put("listChanged", false)
                        }
                    }
                    putJsonObject("serverInfo") {
                        put("name", "AiChallengeMcpServer")
                        put("version", "1.0.0")
                    }
                }
            )

            "tools/list" -> JsonRpcResponseDto(
                id = request.id,
                result = buildJsonObject {
                    put("tools", buildJsonArray {
                        toolRegistryService.listToolDefinitions().forEach { tool ->
                            add(
                                buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("inputSchema", tool.inputSchema)
                                }
                            )
                        }
                    })
                }
            )

            "tools/call" -> {
                val toolName = request.params["name"]?.asString()
                val arguments = request.params["arguments"]?.asObject() ?: JsonObject(emptyMap())

                if (toolName.isNullOrBlank()) {
                    return JsonRpcResponseDto(
                        id = request.id,
                        error = JsonRpcErrorDto(code = -32602, message = "Invalid params: name is required")
                    )
                }

                val result = toolRegistryService.callTool(toolName, arguments)
                    ?: return JsonRpcResponseDto(
                        id = request.id,
                        error = JsonRpcErrorDto(code = -32601, message = "Tool not found: $toolName")
                    )

                JsonRpcResponseDto(
                    id = request.id,
                    result = buildJsonObject {
                        put("isError", result.isError)
                        put("content", buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("type", "text")
                                    put("text", result.text)
                                }
                            )
                        })
                    }
                )
            }

            else -> JsonRpcResponseDto(
                id = request.id,
                error = JsonRpcErrorDto(code = -32601, message = "Method not found: ${request.method}")
            )
        }
    }

    private fun JsonElement.asString(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()
    private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
}
