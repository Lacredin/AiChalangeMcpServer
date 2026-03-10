package com.example.aichalangemcpserver.domain.tool

import kotlinx.serialization.json.JsonObject

data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)
