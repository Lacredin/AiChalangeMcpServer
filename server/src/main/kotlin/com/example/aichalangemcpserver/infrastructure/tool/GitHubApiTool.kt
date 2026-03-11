package com.example.aichalangemcpserver.infrastructure.tool

import com.example.aichalangemcpserver.domain.tool.McpTool
import com.example.aichalangemcpserver.domain.tool.ToolDefinition
import com.example.aichalangemcpserver.domain.tool.ToolExecutionResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File

class GitHubApiTool : McpTool {
    private val json = Json { prettyPrint = true }
    private val token = resolveToken()

    private val client = HttpClient(Java) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    override val definition: ToolDefinition = ToolDefinition(
        name = "github.api",
        description = "Запросы к GitHub API. Поддерживает репозитории и профили, включая запросы к авторизованному пользователю.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("operation") {
                    put("type", "string")
                    put("description", "Операция GitHub API.")
                    putJsonArray("enum") {
                        add(JsonPrimitive("get_repo"))
                        add(JsonPrimitive("list_user_repos"))
                        add(JsonPrimitive("list_authenticated_repos"))
                        add(JsonPrimitive("get_user"))
                        add(JsonPrimitive("get_authenticated_user"))
                    }
                }
                putJsonObject("owner") {
                    put("type", "string")
                    put("description", "Владелец репозитория (для get_repo).")
                }
                putJsonObject("repo") {
                    put("type", "string")
                    put("description", "Название репозитория (для get_repo).")
                }
                putJsonObject("username") {
                    put("type", "string")
                    put("description", "Имя пользователя GitHub (для get_user и list_user_repos).")
                }
                putJsonObject("perPage") {
                    put("type", "integer")
                    put("description", "Количество записей на страницу (для list_user_repos и list_authenticated_repos, 1..100).")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("operation"))
            }
            put("additionalProperties", false)
        }
    )

    override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
        val operation = arguments["operation"]?.jsonPrimitive?.contentOrNull
            ?: inferOperation(arguments)
            ?: return ToolExecutionResult(
                text = "Отсутствует обязательный аргумент: operation. Пример: {\"operation\":\"list_authenticated_repos\",\"perPage\":30}",
                isError = true
            )

        val perPage = (arguments["perPage"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 30).coerceIn(1, 100)

        val url = when (operation) {
            "get_repo" -> {
                val owner = arguments["owner"]?.jsonPrimitive?.contentOrNull
                val repo = arguments["repo"]?.jsonPrimitive?.contentOrNull
                if (owner.isNullOrBlank() || repo.isNullOrBlank()) {
                    return ToolExecutionResult("Для get_repo обязательны owner и repo", isError = true)
                }
                "https://api.github.com/repos/$owner/$repo"
            }

            "list_user_repos" -> {
                val username = arguments["username"]?.jsonPrimitive?.contentOrNull
                if (username.isNullOrBlank()) {
                    return ToolExecutionResult("Для list_user_repos обязателен username", isError = true)
                }
                "https://api.github.com/users/$username/repos?per_page=$perPage"
            }

            "list_authenticated_repos" -> {
                if (token.isNullOrBlank()) {
                    return ToolExecutionResult(
                        "Для list_authenticated_repos требуется токен GITHUB_PERSONAL_ACCESS_TOKEN",
                        isError = true
                    )
                }
                "https://api.github.com/user/repos?per_page=$perPage"
            }

            "get_user" -> {
                val username = arguments["username"]?.jsonPrimitive?.contentOrNull
                if (username.isNullOrBlank()) {
                    return ToolExecutionResult("Для get_user обязателен username", isError = true)
                }
                "https://api.github.com/users/$username"
            }

            "get_authenticated_user" -> {
                if (token.isNullOrBlank()) {
                    return ToolExecutionResult(
                        "Для get_authenticated_user требуется токен GITHUB_PERSONAL_ACCESS_TOKEN",
                        isError = true
                    )
                }
                "https://api.github.com/user"
            }

            else -> return ToolExecutionResult(
                text = "Неизвестная операция: $operation. Доступно: get_repo, list_user_repos, list_authenticated_repos, get_user, get_authenticated_user",
                isError = true
            )
        }

        return runCatching {
            val response = client.get(url) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header(HttpHeaders.UserAgent, "AiChallengeMcpServer")
                token?.takeIf { it.isNotBlank() }?.let { nonEmptyToken ->
                    header(HttpHeaders.Authorization, "Bearer $nonEmptyToken")
                }
            }

            val responseJson = response.body<kotlinx.serialization.json.JsonElement>()
            val isError = response.status.value >= 400
            ToolExecutionResult(text = json.encodeToString(responseJson), isError = isError)
        }.getOrElse { error ->
            ToolExecutionResult(text = "Ошибка запроса к GitHub API: ${error.message}", isError = true)
        }
    }

    private fun inferOperation(arguments: JsonObject): String? {
        val hint = buildString {
            append(arguments["task_context"]?.jsonPrimitive?.contentOrNull.orEmpty())
            append(' ')
            append(arguments["step_description"]?.jsonPrimitive?.contentOrNull.orEmpty())
        }.lowercase()

        if (
            hint.contains("репозитор") ||
            hint.contains("my repos") ||
            hint.contains("repositories") ||
            hint.contains("github")
        ) {
            return "list_authenticated_repos"
        }
        return null
    }

    private fun resolveToken(): String? {
        val fromEnv = System.getenv("GITHUB_PERSONAL_ACCESS_TOKEN")
            ?: System.getenv("GITHUB_TOKEN")
        if (!fromEnv.isNullOrBlank()) return fromEnv

        return discoverSecretFile()
            ?.readLines()
            ?.asSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("#") }
            ?.mapNotNull { line ->
                val idx = line.indexOf('=')
                if (idx <= 0) return@mapNotNull null
                val key = line.substring(0, idx).trim()
                val value = line.substring(idx + 1).trim()
                key to value
            }
            ?.firstOrNull { (key, _) -> key == "GITHUB_PERSONAL_ACCESS_TOKEN" || key == "GITHUB_TOKEN" }
            ?.second
            ?.takeIf { it.isNotBlank() }
    }

    private fun discoverSecretFile(): File? {
        var dir: File? = File(System.getProperty("user.dir"))
        repeat(4) {
            val candidate = File(dir, ".secrets.env")
            if (candidate.exists()) return candidate
            dir = dir?.parentFile
        }
        return null
    }
}
