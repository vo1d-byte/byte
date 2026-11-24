package dev.byte.ide

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import dev.byte.ide.core.Native
import dev.byte.ide.ui.EditorState
import dev.byte.ide.ui.IdeScreen
import dev.byte.ide.ui.Runner
import dev.byte.ide.ui.TerminalSession
import java.io.File

class MainActivity : ComponentActivity() {
    private var session = 0L
    private var terminal: TerminalSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val root = File(filesDir, "projects/default").apply { mkdirs() }
        File(root, "hello.js").takeIf { !it.exists() }?.writeText(
            "// Welcome to Byte\nconsole.log(\"Hello from Byte\")\n",
        )

        session = Native.sessionOpen(root.absolutePath)
        val editor = EditorState(session).also { it.open("hello.js") }
        val term = TerminalSession(root.absolutePath).also { terminal = it }
        val runner = Runner(term, root.absolutePath)

        setContent { IdeScreen(editor, term, runner) }
    }

    override fun onPause() {
        super.onPause()
        if (session != 0L) Native.saveDirty(session)
    }

    override fun onDestroy() {
        super.onDestroy()
        terminal?.close()
        if (session != 0L) {
            Native.saveDirty(session)
            Native.sessionClose(session)
        }
    }
}
