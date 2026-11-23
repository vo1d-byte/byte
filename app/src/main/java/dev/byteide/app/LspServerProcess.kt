package dev.byteide.app

import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Events emitted by a running Language Server Protocol process. */
sealed interface LspEvent {
    data class Message(val value: JSONObject) : LspEvent
    data class Stderr(val line: String) : LspEvent
    data class Exited(val exitCode: Int?) : LspEvent
    data class Failed(val reason: String) : LspEvent
}

/**
 * Owns one local language-server process.
 *
 * The executable must be installed in app-private storage and made executable by
 * the toolchain installer. This class never downloads or executes project scripts.
 */
class LspServerProcess(
    private val command: List<String>,
    private val workingDirectory: File,
    private val environment: Map<String, String> = emptyMap(),
    private val onEvent: (LspEvent) -> Unit
) : AutoCloseable {
    companion object {
        private const val MAX_MESSAGE_BYTES = 8 * 1024 * 1024
    }

    private val writerLock = Any()
    private val requestIds = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, (JSONObject) -> Unit>()
    private val io = Executors.newFixedThreadPool(2)
    @Volatile private var process: Process? = null
    @Volatile private var input: BufferedOutputStream? = null
    @Volatile private var started = false

    @Synchronized
    fun start(): Boolean {
        if (started) return true
        require(command.isNotEmpty()) { "LSP command cannot be empty" }
        require(workingDirectory.isDirectory) { "LSP working directory does not exist" }
        return try {
            val builder = ProcessBuilder(command).directory(workingDirectory)
            builder.environment().putAll(environment)
            val child = builder.start()
            process = child
            input = BufferedOutputStream(child.outputStream)
            started = true
            io.execute { readMessages(child.inputStream) }
            io.execute { readStderr(child.errorStream) }
            io.execute {
                val code = runCatching { child.waitFor() }.getOrNull()
                started = false
                pending.clear()
                onEvent(LspEvent.Exited(code))
            }
            true
        } catch (error: Exception) {
            onEvent(LspEvent.Failed(error.message ?: "Unable to start language server"))
            false
        }
    }

    fun send(message: JSONObject): Boolean {
        val body = message.toString().toByteArray(StandardCharsets.UTF_8)
        if (body.size > MAX_MESSAGE_BYTES || !started) return false
        val header = "Content-Length: ${body.size}\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
        return synchronized(writerLock) {
            runCatching {
                val stream = input ?: return@synchronized false
                stream.write(header)
                stream.write(body)
                stream.flush()
                true
            }.getOrElse {
                onEvent(LspEvent.Failed(it.message ?: "LSP write failed"))
                false
            }
        }
    }

    fun request(method: String, params: JSONObject = JSONObject(), callback: (JSONObject) -> Unit): Int {
        val id = requestIds.getAndIncrement()
        pending[id] = callback
        val message = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .put("params", params)
        if (!send(message)) pending.remove(id)
        return id
    }

    fun notify(method: String, params: JSONObject = JSONObject()): Boolean =
        send(JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params))

    fun initialize(rootUri: String, capabilities: JSONObject = JSONObject()): Int =
        request("initialize", JSONObject()
            .put("processId", android.os.Process.myPid())
            .put("rootUri", rootUri)
            .put("capabilities", capabilities)
            .put("clientInfo", JSONObject().put("name", "Byte IDE").put("version", "0.1.0"))
        ) { response ->
            if (!response.has("error")) notify("initialized")
            onEvent(LspEvent.Message(response))
        }

    fun didOpen(uri: String, languageId: String, version: Int, text: String): Boolean =
        notify("textDocument/didOpen", JSONObject().put("textDocument",
            JSONObject().put("uri", uri).put("languageId", languageId)
                .put("version", version).put("text", text)))

    fun didChange(uri: String, version: Int, text: String): Boolean =
        notify("textDocument/didChange", JSONObject().put("textDocument",
            JSONObject().put("uri", uri).put("version", version))
            .put("contentChanges", org.json.JSONArray().put(JSONObject().put("text", text))))

    fun didClose(uri: String): Boolean =
        notify("textDocument/didClose", JSONObject().put("textDocument", JSONObject().put("uri", uri)))

    fun shutdown() {
        request("shutdown", JSONObject()) { notify("exit"); terminate() }
    }

    fun terminate() {
        synchronized(writerLock) {
            runCatching { input?.close() }
            input = null
        }
        process?.destroy()
        if (process?.isAlive == true) process?.destroyForcibly()
        process = null
        started = false
        pending.clear()
    }

    private fun readMessages(stream: InputStream) {
        val source = BufferedInputStream(stream)
        try {
            while (started) {
                val header = readHeader(source) ?: break
                val length = Regex("(?im)^Content-Length\\s*:\\s*(\\d+)\\s*$")
                    .find(header)?.groupValues?.get(1)?.toIntOrNull()
                    ?: throw IllegalStateException("Invalid LSP Content-Length header")
                require(length in 1..MAX_MESSAGE_BYTES) { "LSP message exceeds size limit" }
                val body = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val count = source.read(body, offset, length - offset)
                    if (count < 0) throw IllegalStateException("Unexpected end of LSP stream")
                    offset += count
                }
                val message = JSONObject(String(body, StandardCharsets.UTF_8))
                val id = message.optInt("id", -1)
                val callback = pending.remove(id)
                if (callback != null) callback(message) else onEvent(LspEvent.Message(message))
            }
        } catch (error: Exception) {
            if (started) onEvent(LspEvent.Failed(error.message ?: "LSP read failed"))
        }
    }

    private fun readHeader(stream: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        var matched = 0
        while (bytes.size() <= 8192) {
            val next = stream.read()
            if (next < 0) return if (bytes.size() == 0) null else throw IllegalStateException("Truncated LSP header")
            bytes.write(next)
            matched = when {
                matched == 0 && next == 13 -> 1
                matched == 1 && next == 10 -> 2
                matched == 2 && next == 13 -> 3
                matched == 3 && next == 10 -> 4
                next == 13 -> 1
                else -> 0
            }
            if (matched == 4) return bytes.toString("US-ASCII")
        }
        throw IllegalStateException("LSP header exceeds 8 KiB")
    }

    private fun readStderr(stream: InputStream) {
        stream.bufferedReader().useLines { lines -> lines.forEach { onEvent(LspEvent.Stderr(it)) } }
    }

    override fun close() {
        if (started) shutdown() else terminate()
        io.shutdown()
    }
}
