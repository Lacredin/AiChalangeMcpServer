package com.example.aichalangemcpserver.domain.tool

import kotlinx.serialization.json.JsonObject

interface McpTool {
    val definition: ToolDefinition
    suspend fun execute(arguments: JsonObject): ToolExecutionResult
}
