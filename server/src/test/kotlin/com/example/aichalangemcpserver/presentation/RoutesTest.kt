package com.example.aichalangemcpserver.presentation

import com.example.aichalangemcpserver.application.ToolRegistryService
import com.example.aichalangemcpserver.infrastructure.db.NoteRepository
import com.example.aichalangemcpserver.infrastructure.tool.CreateNoteTool
import com.example.aichalangemcpserver.infrastructure.tool.GetNotesTool
import com.example.aichalangemcpserver.infrastructure.ws.TimerWebSocketHub
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.server.websocket.WebSockets
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutesTest {
    private val parser = Json { ignoreUnknownKeys = true }

    @Test
    fun `clear notes endpoint removes saved notes`() = runTest {
        val repository = NoteRepository("jdbc:sqlite::memory:")
        val createNoteTool = CreateNoteTool(repository)
        createNoteTool.execute(
            buildJsonObject {
                put("title", JsonPrimitive("Test"))
                put("description", JsonPrimitive("Desc"))
            }
        )
        val registry = ToolRegistryService(tools = listOf(GetNotesTool(repository), createNoteTool))

        testApplication {
            application {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                install(WebSockets)
                routing {
                    Routes(registry, TimerWebSocketHub(), repository).register(this)
                }
            }

            val response = client.post("/api/notes/clear") {
                contentType(ContentType.Application.Json)
            }
            assertEquals(HttpStatusCode.OK, response.status)

            val payload = parser.parseToJsonElement(response.bodyAsText()) as JsonObject
            assertEquals("1", payload["deleted"]!!.jsonPrimitive.content)
            assertEquals(0, repository.getAll().size)
        }
    }

    @Test
    fun `mcp tools call works for creating note and getting notes`() = runTest {
        val repository = NoteRepository("jdbc:sqlite::memory:")
        val registry = ToolRegistryService(
            tools = listOf(
                CreateNoteTool(repository),
                GetNotesTool(repository)
            )
        )

        testApplication {
            application {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                install(WebSockets)
                routing {
                    Routes(registry, TimerWebSocketHub(), repository).register(this)
                }
            }

            val createRequest = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(1))
                put("method", JsonPrimitive("tools/call"))
                putJsonObject("params") {
                    put("name", JsonPrimitive("creating_a_note"))
                    putJsonObject("arguments") {
                        put("title", JsonPrimitive("MCP"))
                        put("description", JsonPrimitive("Создано через tools/call"))
                    }
                }
            }

            val createResponse = client.post("/mcp") {
                contentType(ContentType.Application.Json)
                setBody(createRequest.toString())
            }
            assertEquals(HttpStatusCode.OK, createResponse.status)

            val getRequest = buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(2))
                put("method", JsonPrimitive("tools/call"))
                putJsonObject("params") {
                    put("name", JsonPrimitive("get_note"))
                    putJsonObject("arguments") {}
                }
            }

            val getResponse = client.post("/mcp") {
                contentType(ContentType.Application.Json)
                setBody(getRequest.toString())
            }
            assertEquals(HttpStatusCode.OK, getResponse.status)

            val rpcPayload = parser.parseToJsonElement(getResponse.bodyAsText()).jsonObject
            val contentText = rpcPayload["result"]!!
                .jsonObject["content"]!!
                .jsonArray.first()
                .jsonObject["text"]!!
                .jsonPrimitive.content
            assertTrue(contentText.contains("\"total\": 1"))
            assertTrue(contentText.contains("\"title\": \"MCP\""))
        }
    }
}
