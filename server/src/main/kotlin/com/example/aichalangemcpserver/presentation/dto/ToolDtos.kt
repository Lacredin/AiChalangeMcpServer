package com.example.aichalangemcpserver.presentation.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolDto(
    val name: String,
    val description: String,
    @SerialName("inputSchema")
    val inputSchema: JsonObject
)

@Serializable
data class JsonRpcRequestDto(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class JsonRpcResponseDto(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcErrorDto? = null
)

@Serializable
data class JsonRpcErrorDto(
    val code: Int,
    val message: String
)
