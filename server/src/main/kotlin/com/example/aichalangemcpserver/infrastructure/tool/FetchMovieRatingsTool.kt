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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class FetchMovieRatingsTool(
    private val tmdbApiKey: String?,
    private val omdbApiKey: String?,
    private val client: HttpClient = HttpClient(Java) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }
) : McpTool {
    private val outputJson = Json { prettyPrint = true }

    override val definition: ToolDefinition = ToolDefinition(
        name = "fetch_movie_ratings",
        description = "Для списка movie_id получает рейтинг IMDb и метрики через TMDB + OMDb API.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("movieIds") {
                    put("type", "array")
                    put("description", "Список movie_id из search_new_movies.")
                    putJsonObject("items") {
                        put("type", "integer")
                    }
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("movieIds"))
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
        if (omdbApiKey.isNullOrBlank()) {
            return ToolExecutionResult(
                text = "Не найден OMDb API ключ. Укажите OMDB_API_KEY в переменных окружения или .secrets.env",
                isError = true
            )
        }

        val ids = parseMovieIds(arguments["movieIds"])
        if (ids.isEmpty()) {
            return ToolExecutionResult(
                text = "Аргумент movieIds должен содержать хотя бы один movie_id",
                isError = true
            )
        }

        return runCatching {
            val items = ids.map { tmdbId ->
                val details = fetchTmdbMovieDetails(tmdbId)
                val omdb = details.imdbId?.let { fetchOmdbByImdbId(it) }

                buildJsonObject {
                    put("tmdbId", tmdbId)
                    put("title", details.title)
                    put("imdbId", details.imdbId ?: "N/A")
                    putJsonObject("metrics") {
                        put("imdbRating", omdb?.imdbRating ?: "N/A")
                        put("imdbVotes", omdb?.imdbVotes ?: "N/A")
                        put("metascore", omdb?.metascore ?: "N/A")
                    }
                }
            }

            val payload = buildJsonObject {
                put("total", items.size)
                put(
                    "ratings",
                    buildJsonArray {
                        items.forEach { add(it) }
                    }
                )
            }

            ToolExecutionResult(text = outputJson.encodeToString(payload))
        }.getOrElse { error ->
            ToolExecutionResult(
                text = "Ошибка получения рейтингов: ${error.message}",
                isError = true
            )
        }
    }

    private suspend fun fetchTmdbMovieDetails(tmdbMovieId: Long): TmdbMovieDetailsResponse =
        client.get("https://api.themoviedb.org/3/movie/$tmdbMovieId") {
            parameter("api_key", tmdbApiKey)
            parameter("language", "ru-RU")
        }.body()

    private suspend fun fetchOmdbByImdbId(imdbId: String): OmdbMovieResponse? {
        val response = client.get("https://www.omdbapi.com/") {
            parameter("apikey", omdbApiKey)
            parameter("i", imdbId)
        }.body<OmdbMovieResponse>()

        return response.takeIf { it.response == "True" }
    }

    private fun parseMovieIds(value: kotlinx.serialization.json.JsonElement?): List<Long> {
        val array = value as? JsonArray ?: return emptyList()
        return array.mapNotNull { entry -> entry.jsonPrimitive.contentOrNull?.toLongOrNull() }
    }
}

@Serializable
private data class TmdbMovieDetailsResponse(
    @SerialName("id")
    val id: Long,
    @SerialName("title")
    val title: String,
    @SerialName("imdb_id")
    val imdbId: String? = null
)

@Serializable
private data class OmdbMovieResponse(
    @SerialName("Response")
    val response: String,
    @SerialName("imdbRating")
    val imdbRating: String? = null,
    @SerialName("imdbVotes")
    val imdbVotes: String? = null,
    @SerialName("Metascore")
    val metascore: String? = null
)
