package com.example.aichalangemcpserver

import com.example.aichalangemcpserver.application.ToolRegistryService
import com.example.aichalangemcpserver.infrastructure.config.SecretResolver
import com.example.aichalangemcpserver.infrastructure.db.NoteRepository
import com.example.aichalangemcpserver.infrastructure.tool.CreateNoteTool
import com.example.aichalangemcpserver.infrastructure.tool.FetchMovieRatingsTool
import com.example.aichalangemcpserver.infrastructure.tool.ExampleEchoTool
import com.example.aichalangemcpserver.infrastructure.tool.GetNotesTool
import com.example.aichalangemcpserver.infrastructure.tool.GitHubApiTool
import com.example.aichalangemcpserver.infrastructure.tool.SearchNewMoviesTool
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
import java.io.File
import kotlin.time.Duration.Companion.seconds

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val wsHub = TimerWebSocketHub()
    val secretResolver = SecretResolver()
    val dataDir = File("server-data").apply { mkdirs() }
    val noteRepository = NoteRepository("jdbc:sqlite:${File(dataDir, "notes.db").absolutePath}")

    val toolRegistry = ToolRegistryService(
        tools = listOf(
            ExampleEchoTool(),
            GitHubApiTool(),
            TimerTool(wsHub = wsHub, backgroundScope = backgroundScope),
            SearchNewMoviesTool(tmdbApiKey = secretResolver.get("TMDB_API_KEY")),
            FetchMovieRatingsTool(
                tmdbApiKey = secretResolver.get("TMDB_API_KEY"),
                omdbApiKey = secretResolver.get("OMDB_API_KEY")
            ),
            CreateNoteTool(noteRepository = noteRepository),
            GetNotesTool(noteRepository = noteRepository)
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
        noteRepository.close()
    }

    routing {
        Routes(toolRegistry, wsHub, noteRepository).register(this)
    }
}
