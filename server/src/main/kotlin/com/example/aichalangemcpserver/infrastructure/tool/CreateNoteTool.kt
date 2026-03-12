package com.example.aichalangemcpserver.infrastructure.tool

import com.example.aichalangemcpserver.domain.tool.McpTool
import com.example.aichalangemcpserver.domain.tool.ToolDefinition
import com.example.aichalangemcpserver.domain.tool.ToolExecutionResult
import com.example.aichalangemcpserver.infrastructure.db.NoteRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class CreateNoteTool(
    private val noteRepository: NoteRepository
) : McpTool {
    private val outputJson = Json { prettyPrint = true }

    override val definition: ToolDefinition = ToolDefinition(
        name = "creating_a_note",
        description = "Создает заметку и сохраняет ее в базу данных.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Название заметки.")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "Описание заметки.")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("description"))
            }
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val title = arguments["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val description = arguments["description"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

        if (title.isBlank()) {
            return ToolExecutionResult(
                text = "Аргумент title обязателен",
                isError = true
            )
        }
        if (description.isBlank()) {
            return ToolExecutionResult(
                text = "Аргумент description обязателен",
                isError = true
            )
        }

        val created = noteRepository.create(title = title, description = description)
        val payload = buildJsonObject {
            put("id", created.id)
            put("title", created.title)
            put("description", created.description)
            put("createdAt", created.createdAt)
        }
        return ToolExecutionResult(text = outputJson.encodeToString(payload))
    }
}
