package com.example.aichalangemcpserver.application

import com.example.aichalangemcpserver.domain.tool.McpTool
import com.example.aichalangemcpserver.domain.tool.ToolDefinition
import com.example.aichalangemcpserver.domain.tool.ToolExecutionResult
import kotlinx.serialization.json.JsonObject

class ToolRegistryService(
    tools: List<McpTool>
) {
    private val toolsByName: Map<String, McpTool> = tools.associateBy { it.definition.name }

    fun listToolDefinitions(): List<ToolDefinition> = toolsByName.values.map { it.definition }.sortedBy { it.name }

    suspend fun callTool(name: String, arguments: JsonObject): ToolExecutionResult? {
        val tool = toolsByName[name] ?: return null
        return tool.execute(arguments)
    }
}
