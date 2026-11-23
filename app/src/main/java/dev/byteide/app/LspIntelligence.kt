package dev.byteide.app

import org.json.JSONArray
import org.json.JSONObject

/** UTF-16 line/character position as defined by the LSP default encoding. */
data class LspPosition(val line: Int, val character: Int)
data class LspRange(val start: LspPosition, val end: LspPosition)
data class LspDiagnostic(
    val range: LspRange,
    val message: String,
    val severity: Int?,
    val code: String?,
    val source: String?
)
data class LspCompletionItem(
    val label: String,
    val detail: String?,
    val documentation: String?,
    val insertText: String?,
    val kind: Int?,
    val sortText: String?
)
data class LspLocation(val uri: String, val range: LspRange)
data class LspHover(val markdown: String)

/**
 * Typed feature facade over [LspServerProcess].
 *
 * The owner of the process remains responsible for initialization and document
 * lifetime. This facade translates common editor actions into LSP requests and
 * turns server notifications into typed diagnostics.
 */
class LspIntelligence(
    private val server: LspServerProcess,
    private val onDiagnostics: (uri: String, version: Int?, items: List<LspDiagnostic>) -> Unit,
    private val onServerNotification: (JSONObject) -> Unit = {}
) {
    fun didOpen(uri: String, languageId: String, version: Int, text: String): Boolean =
        server.didOpen(uri, languageId, version, text)

    fun didChange(uri: String, version: Int, text: String): Boolean =
        server.didChange(uri, version, text)

    fun didClose(uri: String): Boolean = server.didClose(uri)

    fun completion(uri: String, position: LspPosition, callback: (List<LspCompletionItem>) -> Unit): Int =
        server.request("textDocument/completion", textPositionParams(uri, position)) { response ->
            callback(parseCompletion(response.opt("result")))
        }

    fun hover(uri: String, position: LspPosition, callback: (LspHover?) -> Unit): Int =
        server.request("textDocument/hover", textPositionParams(uri, position)) { response ->
            callback(parseHover(response.optJSONObject("result")))
        }

    fun definition(uri: String, position: LspPosition, callback: (List<LspLocation>) -> Unit): Int =
        server.request("textDocument/definition", textPositionParams(uri, position)) { response ->
            callback(parseLocations(response.opt("result")))
        }

    fun references(
        uri: String,
        position: LspPosition,
        includeDeclaration: Boolean = true,
        callback: (List<LspLocation>) -> Unit
    ): Int {
        val params = textPositionParams(uri, position)
            .put("context", JSONObject().put("includeDeclaration", includeDeclaration))
        return server.request("textDocument/references", params) { response ->
            callback(parseLocations(response.opt("result")))
        }
    }

    fun rename(
        uri: String,
        position: LspPosition,
        newName: String,
        callback: (JSONObject?) -> Unit
    ): Int {
        val params = textPositionParams(uri, position).put("newName", newName)
        return server.request("textDocument/rename", params) { response ->
            callback(response.optJSONObject("result"))
        }
    }

    fun formatting(uri: String, tabSize: Int = 4, insertSpaces: Boolean = true,
                   callback: (JSONArray) -> Unit): Int {
        val params = JSONObject()
            .put("textDocument", JSONObject().put("uri", uri))
            .put("options", JSONObject().put("tabSize", tabSize).put("insertSpaces", insertSpaces))
        return server.request("textDocument/formatting", params) { response ->
            callback(response.optJSONArray("result") ?: JSONArray())
        }
    }

    /** Forward each incoming LSP notification here from the process event listener. */
    fun acceptNotification(message: JSONObject) {
        when (message.optString("method")) {
            "textDocument/publishDiagnostics" -> {
                val params = message.optJSONObject("params") ?: return
                val uri = params.optString("uri")
                val version = if (params.has("version") && !params.isNull("version")) params.optInt("version") else null
                onDiagnostics(uri, version, parseDiagnostics(params.optJSONArray("diagnostics") ?: JSONArray()))
            }
            else -> onServerNotification(message)
        }
    }

    private fun textPositionParams(uri: String, position: LspPosition): JSONObject =
        JSONObject()
            .put("textDocument", JSONObject().put("uri", uri))
            .put("position", JSONObject().put("line", position.line.coerceAtLeast(0))
                .put("character", position.character.coerceAtLeast(0)))

    private fun parseCompletion(value: Any?): List<LspCompletionItem> {
        val items = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("items") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                add(LspCompletionItem(
                    label = item.optString("label"),
                    detail = item.stringOrNull("detail"),
                    documentation = parseMarkup(item.opt("documentation")),
                    insertText = item.stringOrNull("insertText"),
                    kind = if (item.has("kind")) item.optInt("kind") else null,
                    sortText = item.stringOrNull("sortText")
                ))
            }
        }
    }

    private fun parseHover(value: JSONObject?): LspHover? {
        if (value == null) return null
        val contents = parseMarkup(value.opt("contents")).orEmpty()
        return contents.takeIf { it.isNotBlank() }?.let(::LspHover)
    }

    private fun parseMarkup(value: Any?): String? = when (value) {
        is String -> value
        is JSONObject -> value.optString("value").takeIf { it.isNotBlank() }
        is JSONArray -> (0 until value.length()).mapNotNull { parseMarkup(value.opt(it)) }
            .joinToString("\n").takeIf { it.isNotBlank() }
        else -> null
    }

    private fun parseLocations(value: Any?): List<LspLocation> {
        val values = when (value) {
            is JSONArray -> value
            is JSONObject -> JSONArray().put(value)
            else -> JSONArray()
        }
        return buildList {
            for (index in 0 until values.length()) {
                val item = values.optJSONObject(index) ?: continue
                val uri = item.optString("uri")
                val range = item.optJSONObject("range") ?: item.optJSONObject("targetSelectionRange") ?: continue
                parseRange(range)?.let { if (uri.isNotBlank()) add(LspLocation(uri, it)) }
            }
        }
    }

    private fun parseDiagnostics(values: JSONArray): List<LspDiagnostic> = buildList {
        for (index in 0 until values.length()) {
            val item = values.optJSONObject(index) ?: continue
            val range = parseRange(item.optJSONObject("range")) ?: continue
            add(LspDiagnostic(
                range = range,
                message = item.optString("message"),
                severity = if (item.has("severity")) item.optInt("severity") else null,
                code = item.opt("code")?.toString(),
                source = item.stringOrNull("source")
            ))
        }
    }

    private fun parseRange(value: JSONObject?): LspRange? {
        if (value == null) return null
        val start = value.optJSONObject("start") ?: return null
        val end = value.optJSONObject("end") ?: return null
        return LspRange(
            LspPosition(start.optInt("line"), start.optInt("character")),
            LspPosition(end.optInt("line"), end.optInt("character"))
        )
    }

    private fun JSONObject.stringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
