package com.cleo.cleos.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Versioned, structured chat-table payload in the EXISTING MessageEntity.content.
 * No Room migration is needed, so backups, forwarding, export and old messages survive.
 */
@Serializable
data class ChatTable(
    val title: String = "",
    val columns: List<String>,
    val rows: List<List<String>>,
)

sealed interface ChatTablePart {
    data class Words(val text: String) : ChatTablePart
    data class Grid(val table: ChatTable) : ChatTablePart
}

object ChatTables {
    private const val PREFIX = "CLEOS_TABLE_V1:"
    private val json = Json { ignoreUnknownKeys = true }

    fun validate(table: ChatTable): ChatTable? {
        val cols = table.columns.size
        if (cols !in 2..6 || table.rows.isEmpty() || table.rows.size > 120) return null
        if (table.rows.any { it.size != cols }) return null
        val clean = table.copy(
            title = table.title.trim().take(90),
            columns = table.columns.map { it.trim().take(80) },
            rows = table.rows.map { row -> row.map { it.trim().take(600) } },
        )
        if (clean.columns.any { it.isEmpty() }) return null
        return clean
    }

    fun encode(table: ChatTable): String {
        val valid = requireNotNull(validate(table)) { "Invalid table" }
        return PREFIX + json.encodeToString(valid)
    }

    fun decode(text: String): ChatTable? {
        if (!text.startsWith(PREFIX)) return null
        return runCatching {
            validate(json.decodeFromString<ChatTable>(text.removePrefix(PREFIX)))
        }.getOrNull()
    }

    /** Model tool receives real arrays, not Markdown or JSON text quoted in a string. */
    fun fromArgs(obj: JsonObject): ChatTable? {
        fun str(value: kotlinx.serialization.json.JsonElement): String? =
            (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        val title = obj["title"]?.let(::str).orEmpty()
        val columns = (obj["columns"] as? JsonArray)?.map { str(it) ?: return null }
            ?: return null
        val rows = (obj["rows"] as? JsonArray)?.map { element ->
            (element as? JsonArray)?.map { str(it) ?: return null } ?: return null
        } ?: return null
        return validate(ChatTable(title, columns, rows))
    }

    private fun splitRow(line: String): List<String> {
        val source = line.trim().removePrefix("|").removeSuffix("|")
        val output = ArrayList<String>()
        val cell = StringBuilder()
        var index = 0
        while (index < source.length) {
            val ch = source[index]
            if (ch == '\\' && index + 1 < source.length && source[index + 1] == '|') {
                cell.append('|')
                index += 2
            } else if (ch == '|') {
                output += cell.toString().trim()
                cell.clear()
                index++
            } else {
                cell.append(ch)
                index++
            }
        }
        output += cell.toString().trim()
        return output
    }

    private fun separator(line: String, count: Int): Boolean {
        val cells = splitRow(line)
        return cells.size == count && cells.all { Regex("^:?-{3,}:?$").matches(it.replace(" ", "")) }
    }

    /**
     * Supports legacy Markdown tables, with or without external |, multiple grids,
     * surrounding prose, and fenced code that must NEVER be interpreted as a table.
     * Invalid / incomplete grids remain ordinary text instead of losing information.
     */
    fun parts(input: String): List<ChatTablePart> {
        decode(input)?.let { return listOf(ChatTablePart.Grid(it)) }
        val lines = input.lines()
        val result = ArrayList<ChatTablePart>()
        val words = StringBuilder()
        fun flush() {
            if (words.isNotEmpty()) {
                result += ChatTablePart.Words(words.toString().trimEnd('\n'))
                words.clear()
            }
        }
        var index = 0
        var inFence = false
        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("```") || line.trimStart().startsWith("~~~")) {
                inFence = !inFence
            }
            if (!inFence && index + 2 < lines.size && line.contains('|')) {
                val headers = splitRow(line)
                if (headers.size in 2..6 && headers.all { it.isNotBlank() } &&
                    separator(lines[index + 1], headers.size)) {
                    var next = index + 2
                    val body = ArrayList<List<String>>()
                    while (next < lines.size && body.size < 120) {
                        val item = lines[next]
                        if (item.isBlank() || !item.contains('|')) break
                        val cols = splitRow(item)
                        if (cols.size != headers.size) break
                        body += cols
                        next++
                    }
                    if (body.isNotEmpty()) {
                        flush()
                        result += ChatTablePart.Grid(ChatTable(columns = headers, rows = body))
                        index = next
                        continue
                    }
                }
            }
            words.append(line)
            if (index != lines.lastIndex) words.append('\n')
            index++
        }
        flush()
        return result.ifEmpty { listOf(ChatTablePart.Words(input)) }
    }

    fun tsv(table: ChatTable): String =
        (listOf(table.columns) + table.rows).joinToString("\n") {
            it.joinToString("\t") { cell -> cell.replace('\t', ' ').replace('\n', ' ') }
        }
}
