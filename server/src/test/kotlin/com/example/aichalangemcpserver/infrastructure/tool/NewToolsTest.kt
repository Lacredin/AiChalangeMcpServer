package com.example.aichalangemcpserver.infrastructure.tool

import com.example.aichalangemcpserver.infrastructure.db.NoteRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NewToolsTest {
    private val parser = Json { ignoreUnknownKeys = true }

    @Test
    fun `search_new_movies returns ids and titles`() = runTest {
        val client = testClient { request ->
            assertTrue(request.url.toString().contains("now_playing"))
            respond(
                content = """
                    {
                      "results": [
                        { "id": 101, "title": "Film 1" },
                        { "id": 102, "title": "Film 2" }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }

        val tool = SearchNewMoviesTool(tmdbApiKey = "tmdb-key", client = client)
        val result = tool.execute(buildJsonObject { put("count", JsonPrimitive(2)) })

        assertFalse(result.isError)
        val payload = parser.parseToJsonElement(result.text).jsonObject
        val movies = payload["movies"]!!.jsonArray
        assertEquals(2, payload["total"]!!.jsonPrimitive.content.toInt())
        assertEquals("101", movies[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("Film 1", movies[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `fetch_movie_ratings returns structured ratings`() = runTest {
        val client = testClient { request ->
            when {
                request.url.toString().contains("/3/movie/101") -> respond(
                    content = """{ "id": 101, "title": "Film 1", "imdb_id": "tt0111111" }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )

                request.url.toString().contains("omdbapi.com") -> respond(
                    content = """{ "Response":"True", "imdbRating":"7.8", "imdbVotes":"123,456", "Metascore":"70" }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )

                else -> respond(
                    content = """{ "error": "unexpected" }""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }

        val tool = FetchMovieRatingsTool(
            tmdbApiKey = "tmdb-key",
            omdbApiKey = "omdb-key",
            client = client
        )
        val result = tool.execute(
            buildJsonObject {
                put(
                    "movieIds",
                    buildJsonArray {
                        add(JsonPrimitive(101))
                    }
                )
            }
        )

        assertFalse(result.isError)
        val payload = parser.parseToJsonElement(result.text).jsonObject
        val first = payload["ratings"]!!.jsonArray.first().jsonObject
        assertEquals("101", first["tmdbId"]!!.jsonPrimitive.content)
        assertEquals("tt0111111", first["imdbId"]!!.jsonPrimitive.content)
        assertEquals("7.8", first["metrics"]!!.jsonObject["imdbRating"]!!.jsonPrimitive.content)
    }

    @Test
    fun `creating_a_note and get_note work with database`() = runTest {
        val repository = NoteRepository("jdbc:sqlite::memory:")
        val createNoteTool = CreateNoteTool(noteRepository = repository)
        val getNotesTool = GetNotesTool(noteRepository = repository)

        val createResult = createNoteTool.execute(
            buildJsonObject {
                put("title", JsonPrimitive("Shopping"))
                put("description", JsonPrimitive("Buy milk"))
            }
        )
        assertFalse(createResult.isError)

        val listResult = getNotesTool.execute(JsonObject(emptyMap()))
        assertFalse(listResult.isError)

        val payload = parser.parseToJsonElement(listResult.text).jsonObject
        val notes = payload["notes"]!!.jsonArray
        assertEquals(1, payload["total"]!!.jsonPrimitive.content.toInt())
        assertEquals("Shopping", notes[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    private fun testClient(handler: MockRequestHandler): HttpClient {
        val engine = MockEngine(handler)
        return HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }
}
