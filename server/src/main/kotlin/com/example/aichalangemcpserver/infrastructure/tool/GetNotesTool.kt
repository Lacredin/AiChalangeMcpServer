package com.example.aichalangemcpserver.infrastructure.tool

import com.example.aichalangemcpserver.domain.tool.McpTool
import com.example.aichalangemcpserver.domain.tool.ToolDefinition
import com.example.aichalangemcpserver.domain.tool.ToolExecutionResult
import com.example.aichalangemcpserver.infrastructure.db.NoteRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class GetNotesTool(
    private val noteRepository: NoteRepository
) : McpTool {
    private val outputJson = Json { prettyPrint = true }

    override val definition: ToolDefinition = ToolDefinition(
        name = "get_note",
        description = "Возвращает все заметки из базы данных.",
        inputSchema = buildJsonObject {
            put("type", "object")
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val notes = noteRepository.getAll()
        val payload = buildJsonObject {
            put("total", notes.size)
            put(
                "notes",
                buildJsonArray {
                    notes.forEach { note ->
                        add(
                            buildJsonObject {
                                put("id", note.id)
                                put("title", note.title)
                                put("description", note.description)
                                put("createdAt", note.createdAt)
                            }
                        )
                    }
                }
            )
        }
        return ToolExecutionResult(text = outputJson.encodeToString(payload))
    }
}
