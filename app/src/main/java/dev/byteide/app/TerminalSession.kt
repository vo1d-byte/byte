package dev.byteide.app

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object NativeTerminalBridge {
    val isAvailable: Boolean = runCatching { System.loadLibrary("byte_core") }.isSuccess
    @JvmStatic external fun nativeCreate(program: String, workingDirectory: String, rows: Int, columns: Int): Long
    @JvmStatic external fun nativeWrite(handle: Long, input: String): Boolean
    @JvmStatic external fun nativePoll(handle: Long): String
    @JvmStatic external fun nativePollScreen(handle: Long): String
    @JvmStatic external fun nativeCursorInfo(handle: Long): String
    @JvmStatic external fun nativeIsRunning(handle: Long): Boolean
    @JvmStatic external fun nativeResize(handle: Long, rows: Int, columns: Int): Boolean
    @JvmStatic external fun nativeSignal(handle: Long, signal: Int): Boolean
    @JvmStatic external fun nativeClose(handle: Long)
}

/**
 * Terminal session with a native PTY when byte_core is packaged.
 * A pipe-backed shell fallback is retained for development builds without JNI.
 */
class TerminalSession(
    private val workingDirectory: File,
    private val onOutput: (String) -> Unit,
    private val onState: (String) -> Unit = {}
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val closed = AtomicBoolean(false)
    @Volatile private var nativeHandle = 0L
    @Volatile private var fallbackProcess: Process? = null
    @Volatile private var fallbackWriter: BufferedWriter? = null
    @Volatile private var started = false
    @Volatile private var terminalRows = 24
    @Volatile private var terminalColumns = 80
    private val fallbackOutput = StringBuilder()

    fun start() {
        if (closed.get() || started) return
        started = true
        io.execute {
            try {
                require(workingDirectory.isDirectory) { "Workspace directory is unavailable" }
                if (NativeTerminalBridge.isAvailable) {
                    val handle = NativeTerminalBridge.nativeCreate("/system/bin/sh", workingDirectory.absolutePath, terminalRows, terminalColumns)
                    if (handle == 0L) {
                        startPipeFallback()
                    } else {
                        nativeHandle = handle
                        main.post { if (!closed.get()) onState("Native PTY · shell started · ${terminalRows}×${terminalColumns}") }
                        pollNative(handle)
                    }
                } else {
                    startPipeFallback()
                }
            } catch (error: Exception) {
                started = false
                main.post { if (!closed.get()) onState("Terminal unavailable: ${error.message}") }
            }
        }
    }

    private fun pollNative(handle: Long) {
        while (!closed.get() && nativeHandle == handle) {
            val output = runCatching { NativeTerminalBridge.nativePollScreen(handle) }.getOrDefault("")
            if (output.isNotEmpty()) main.post { if (!closed.get()) onOutput(output) }
            val running = runCatching { NativeTerminalBridge.nativeIsRunning(handle) }.getOrDefault(false)
            if (!running) {
                // Allow the PTY reader thread to deliver bytes written immediately before exit.
                repeat(3) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) { return }
                    val tail = runCatching { NativeTerminalBridge.nativePoll(handle) }.getOrDefault("")
                    if (tail.isNotEmpty()) main.post { if (!closed.get()) onOutput(tail) }
                }
                nativeHandle = 0L
                started = false
                main.post { if (!closed.get()) onState("PTY process exited") }
                break
            }
            try { Thread.sleep(24) } catch (_: InterruptedException) { break }
        }
    }

    private fun startPipeFallback() {
        val shell = File("/system/bin/sh")
        require(shell.canExecute()) { "Android system shell is unavailable" }
        val child = ProcessBuilder(shell.absolutePath).directory(workingDirectory).redirectErrorStream(true).start()
        fallbackProcess = child
        fallbackWriter = BufferedWriter(OutputStreamWriter(child.outputStream, Charsets.UTF_8))
        main.post { if (!closed.get()) onState("Pipe fallback · PTY unavailable") }
        BufferedReader(InputStreamReader(child.inputStream, Charsets.UTF_8)).use { reader ->
            val buffer = CharArray(2048)
            while (!closed.get()) {
                val count = reader.read(buffer)
                if (count < 0) break
                val chunk = String(buffer, 0, count)
                fallbackOutput.append(chunk)\n                if (fallbackOutput.length > 24000) fallbackOutput.delete(0, fallbackOutput.length - 24000)\n                val snapshot = fallbackOutput.toString()\n                main.post { if (!closed.get()) onOutput(snapshot) }
            }
        }
        val code = runCatching { child.waitFor() }.getOrNull()
        fallbackProcess = null
        started = false
        main.post { if (!closed.get()) onState("Shell exited ${code ?: ""}") }
    }

    fun execute(command: String) {
        if (command.isBlank() || closed.get()) return
        io.execute {
            val handle = nativeHandle
            if (handle != 0L) {
                if (!runCatching { NativeTerminalBridge.nativeWrite(handle, command + "\n") }.getOrDefault(false)) {
                    main.post { if (!closed.get()) onState("PTY input failed") }
                }
            } else {
                runCatching {
                    val writer = fallbackWriter ?: error("Shell is not running")
                    writer.write(command)
                    writer.newLine()
                    writer.flush()
                }.onFailure { error -> main.post { if (!closed.get()) onState("Command failed: ${error.message}") } }
            }
        }
    }

    fun writeInput(input: String) {
        if (closed.get()) return
        io.execute {
            val handle = nativeHandle
            if (handle != 0L) runCatching { NativeTerminalBridge.nativeWrite(handle, input) }
            else runCatching { fallbackWriter?.write(input); fallbackWriter?.flush() }
        }
    }

    fun resize(rows: Int, columns: Int) {
        terminalRows = rows.coerceIn(1, 300)
        terminalColumns = columns.coerceIn(1, 500)
        val handle = nativeHandle
        if (handle != 0L) runCatching { NativeTerminalBridge.nativeResize(handle, terminalRows, terminalColumns) }
    }

    fun sendInterruptByte() {
        val handle = nativeHandle
        if (handle != 0L) {
            runCatching { NativeTerminalBridge.nativeSignal(handle, 2) }
        } else {
            // A pipe has no terminal line discipline; this byte may be read as ordinary input.
            io.execute { runCatching { fallbackWriter?.write(3); fallbackWriter?.flush() } }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val handle = nativeHandle
        nativeHandle = 0L
        if (handle != 0L) runCatching { NativeTerminalBridge.nativeClose(handle) }
        runCatching { fallbackWriter?.close() }
        fallbackProcess?.destroy()
        fallbackProcess = null
        fallbackWriter = null
        io.shutdownNow()
    }
}
