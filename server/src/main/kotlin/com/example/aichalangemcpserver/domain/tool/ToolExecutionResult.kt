package com.example.aichalangemcpserver.domain.tool

data class ToolExecutionResult(
    val text: String,
    val isError: Boolean = false
)
