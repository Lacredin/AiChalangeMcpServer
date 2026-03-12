package com.example.aichalangemcpserver.infrastructure.db

import com.example.aichalangemcpserver.domain.note.NoteRecord
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.time.Instant

class NoteRepository(
    jdbcUrl: String
) : AutoCloseable {
    private val connection: Connection = DriverManager.getConnection(jdbcUrl).apply {
        createStatement().use { statement ->
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS notes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    description TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    @Synchronized
    fun create(title: String, description: String): NoteRecord {
        val createdAt = Instant.now().toString()
        val sql = "INSERT INTO notes(title, description, created_at) VALUES(?, ?, ?)"

        val id = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setString(1, title)
            statement.setString(2, description)
            statement.setString(3, createdAt)
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                if (keys.next()) keys.getLong(1) else 0L
            }
        }

        return NoteRecord(
            id = id,
            title = title,
            description = description,
            createdAt = createdAt
        )
    }

    @Synchronized
    fun getAll(): List<NoteRecord> =
        connection.prepareStatement(
            "SELECT id, title, description, created_at FROM notes ORDER BY id ASC"
        ).use { statement ->
            statement.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            NoteRecord(
                                id = rs.getLong("id"),
                                title = rs.getString("title"),
                                description = rs.getString("description"),
                                createdAt = rs.getString("created_at")
                            )
                        )
                    }
                }
            }
        }

    @Synchronized
    fun clearAll(): Int =
        connection.createStatement().use { statement ->
            statement.executeUpdate("DELETE FROM notes")
        }

    override fun close() {
        connection.close()
    }
}
