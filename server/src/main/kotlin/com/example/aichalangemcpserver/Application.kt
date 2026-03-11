package com.example.aichalangemcpserver

import com.example.aichalangemcpserver.application.ToolRegistryService
import com.example.aichalangemcpserver.infrastructure.tool.ExampleEchoTool
import com.example.aichalangemcpserver.infrastructure.tool.GitHubApiTool
import com.example.aichalangemcpserver.infrastructure.tool.TimerTool
import com.example.aichalangemcpserver.infrastructure.ws.TimerWebSocketHub
import com.example.aichalangemcpserver.presentation.Routes
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.websocket.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val wsHub = TimerWebSocketHub()

    val toolRegistry = ToolRegistryService(
        tools = listOf(
            ExampleEchoTool(),
            GitHubApiTool(),
            TimerTool(wsHub = wsHub, backgroundScope = backgroundScope)
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
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
    }

    monitor.subscribe(ApplicationStopping) {
        backgroundScope.cancel("Application stopping")
    }

    routing {
        Routes(toolRegistry, wsHub).register(this)
    }
}
