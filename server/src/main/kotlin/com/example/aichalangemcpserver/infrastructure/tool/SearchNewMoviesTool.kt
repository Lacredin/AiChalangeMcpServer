package com.example.aichalangemcpserver.infrastructure.tool

import com.example.aichalangemcpserver.domain.tool.McpTool
import com.example.aichalangemcpserver.domain.tool.ToolDefinition
import com.example.aichalangemcpserver.domain.tool.ToolExecutionResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

class SearchNewMoviesTool(
    private val tmdbApiKey: String?,
    private val client: HttpClient = HttpClient(Java) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) : McpTool {
    private val outputJson = Json { prettyPrint = true }

    override val definition: ToolDefinition = ToolDefinition(
        name = "search_new_movies",
        description = "Ищет новинки фильмов через TMDB API и возвращает id и название.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("count") {
                    put("type", "integer")
                    put("minimum", 1)
                    put("maximum", 20)
                    put("description", "Количество новинок для выдачи.")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("count"))
            }
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        if (tmdbApiKey.isNullOrBlank()) {
            return ToolExecutionResult(
                text = "Не найден TMDB API ключ. Укажите TMDB_API_KEY в переменных окружения или .secrets.env",
                isError = true
            )
        }

        val count = arguments["count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        if (count == null || count <= 0) {
            return ToolExecutionResult(
                text = "Аргумент count должен быть целым числом больше 0",
                isError = true
            )
        }

        return runCatching {
            val response = client.get("https://api.themoviedb.org/3/movie/now_playing") {
                parameter("api_key", tmdbApiKey)
                parameter("language", "ru-RU")
                parameter("page", 1)
            }.body<TmdbNowPlayingResponse>()

            val movies = response.results.take(count.coerceAtMost(20))
            val payload = buildJsonObject {
                put("total", movies.size)
                put(
                    "movies",
                    buildJsonArray {
                        movies.forEach { movie ->
                            add(
                                buildJsonObject {
                                    put("id", movie.id)
                                    put("title", movie.title)
                                }
                            )
                        }
                    }
                )
            }

            ToolExecutionResult(text = outputJson.encodeToString(payload))
        }.getOrElse { error ->
            ToolExecutionResult(
                text = "Ошибка запроса к TMDB API: ${error.message}",
                isError = true
            )
        }
    }
}

@Serializable
private data class TmdbNowPlayingResponse(
    @SerialName("results")
    val results: List<TmdbMovieItem>
)

@Serializable
private data class TmdbMovieItem(
    @SerialName("id")
    val id: Long,
    @SerialName("title")
    val title: String
)
