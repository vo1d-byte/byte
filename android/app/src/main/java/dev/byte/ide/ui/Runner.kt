package dev.byte.ide.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * v0.1 run support: sends a command for the file's extension to the terminal.
 * Later this reads commands from the toolchain manifests (`[language] run`).
 */
class Runner(private val terminal: TerminalSession, private val root: String) {
    var log by mutableStateOf("")
        private set

    fun note(msg: String) {
        log = (log + msg + "\n").takeLast(20_000)
    }

    fun run(path: String): Boolean {
        val cmd = when (path.substringAfterLast('.', "")) {
            "js", "mjs" -> "node"
            "py" -> "python3"
            "go" -> "go run"
            "sh" -> "sh"
            "rb" -> "ruby"
            else -> null
        }
        if (cmd == null) {
            note("No run command for $path yet. Install a toolchain or add one in toolchains/.")
            return false
        }
        note("Running $path")
        terminal.send("cd '$root' && $cmd '$path'\n")
        return true
    }

    companion object {
        fun commentPrefix(path: String): String = when (path.substringAfterLast('.', "")) {
            "py", "sh", "rb", "toml", "yml", "yaml" -> "#"
            "html", "xml" -> "<!--"
            "sql", "lua" -> "--"
            else -> "//"
        }
    }
}
