package dev.byteide.app

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persistent Android app-private shell using piped stdio.
 * This is not a PTY: terminal control sequences and job control are not supported.
 */
class TerminalSession(
    private val workingDirectory: File,
    private val onOutput: (String) -> Unit,
    private val onState: (String) -> Unit = {}
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val io: ExecutorService = Executors.newCachedThreadPool()
    private val closed = AtomicBoolean(false)
    @Volatile private var process: Process? = null
    @Volatile private var writer: BufferedWriter? = null

    fun start() {
        io.execute {
            if (closed.get() || process != null) return@execute
            try {
                require(workingDirectory.isDirectory) { "Workspace directory is unavailable" }
                val shell = File("/system/bin/sh")
                require(shell.canExecute()) { "Android system shell is unavailable" }
                val child = ProcessBuilder(shell.absolutePath)
                    .directory(workingDirectory)
                    .redirectErrorStream(true)
                    .start()
                process = child
                writer = BufferedWriter(OutputStreamWriter(child.outputStream, Charsets.UTF_8))
                main.post { if (!closed.get()) onState("Shell started · ${workingDirectory.name}") }
                io.execute {
                    try {
                        BufferedReader(InputStreamReader(child.inputStream, Charsets.UTF_8)).use { reader ->
                            val buffer = CharArray(2048)
                            while (!closed.get()) {
                                val count = reader.read(buffer)
                                if (count < 0) break
                                val chunk = String(buffer, 0, count)
                                main.post { if (!closed.get()) onOutput(chunk) }
                            }
                        }
                    } catch (error: Exception) {
                        if (!closed.get()) main.post { onState("Output error: ${error.message}") }
                    }
                    val code = runCatching { child.waitFor() }.getOrNull()
                    process = null
                    writer = null
                    if (!closed.get()) main.post { onState("Shell exited ${code ?: ""}") }
                }
            } catch (error: Exception) {
                main.post { if (!closed.get()) onState("Shell unavailable: ${error.message}") }
            }
        }
    }

    fun execute(command: String) {
        if (command.isBlank() || closed.get()) return
        io.execute {
            try {
                val activeWriter = writer ?: throw IllegalStateException("Shell is not running")
                activeWriter.write(command)
                activeWriter.newLine()
                activeWriter.flush()
            } catch (error: Exception) {
                main.post { if (!closed.get()) onState("Command failed: ${error.message}") }
            }
        }
    }

    /** Writes an ETX byte. Actual foreground-process signal delivery requires a PTY. */
    fun sendInterruptByte() {
        if (closed.get()) return
        io.execute {
            runCatching {
                writer?.write(3)
                writer?.flush()
            }.onFailure { error ->
                main.post { if (!closed.get()) onState("Interrupt unavailable: ${error.message}") }
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { writer?.close() }
        process?.destroy()
        process = null
        writer = null
        io.shutdownNow()
    }
}
