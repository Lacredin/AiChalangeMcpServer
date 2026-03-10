package com.example.aichalangemcpserver

import com.example.aichalangemcpserver.application.ToolRegistryService
import com.example.aichalangemcpserver.infrastructure.tool.ExampleEchoTool
import com.example.aichalangemcpserver.infrastructure.tool.GitHubApiTool
import com.example.aichalangemcpserver.presentation.Routes
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val toolRegistry = ToolRegistryService(
        tools = listOf(
            ExampleEchoTool(),
            GitHubApiTool()
        )
    )

    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            }
        )
    }

    routing {
        Routes(toolRegistry).register(this)
    }
}
