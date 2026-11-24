package dev.byte.ide.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.byte.ide.core.Native
import kotlin.concurrent.thread

/**
 * Raw terminal output. ANSI/VT escape handling is not implemented yet
 * (planned: a Rust `vte` emulator); escapes are stripped for readability.
 */
class TerminalSession(cwd: String, program: String = "/system/bin/sh") {
    private val handle = Native.termSpawn(program, cwd, 24, 80)
    var output by mutableStateOf("")
        private set
    val alive get() = handle > 0 && Native.termAlive(handle)

    private val ansi = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]|\u001B\\][^\u0007]*\u0007")

    init {
        if (handle > 0) {
            thread(name = "term-reader", isDaemon = true) {
                while (true) {
                    val bytes = Native.termRead(handle) ?: break
                    val text = ansi.replace(String(bytes, Charsets.UTF_8), "").replace("\r", "")
                    output = (output + text).takeLast(60_000)
                }
            }
        } else {
            output = "Could not start a shell.\n"
        }
    }

    fun send(text: String) {
        if (handle > 0) Native.termWrite(handle, text.toByteArray())
    }

    fun ctrlC() = send("\u0003")

    fun close() {
        if (handle > 0) Native.termClose(handle)
    }
}
