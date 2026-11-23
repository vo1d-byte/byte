package dev.byteide.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class LspServerProfile(
    val languageId: String,
    val command: List<String>,
    val environment: Map<String, String> = emptyMap()
)

data class LspWorkspaceSnapshot(
    val runningLanguages: Set<String> = emptySet(),
    val diagnosticsByUri: Map<String, List<LspDiagnostic>> = emptyMap(),
    val statusByLanguage: Map<String, String> = emptyMap(),
    val lastServerMessage: String? = null
)

/** Owns one local language-server process per language in a workspace. */
class LspWorkspaceManager(
    private val toolchainsRoot: File,
    private val workspaceRoot: File,
    private val onSnapshot: (LspWorkspaceSnapshot) -> Unit = {}
) : AutoCloseable {
    private data class OpenDocument(
        val uri: String, val languageId: String, var version: Int, var text: String
    )
    private data class ServerSession(
        val process: LspServerProcess,
        val intelligence: LspIntelligence,
        var initialized: Boolean = false
    )

    private val main = Handler(Looper.getMainLooper())
    private val profiles = ConcurrentHashMap<String, LspServerProfile>()
    private val sessions = ConcurrentHashMap<String, ServerSession>()
    private val documents = ConcurrentHashMap<String, OpenDocument>()
    @Volatile private var closed = false

    var snapshot by mutableStateOf(LspWorkspaceSnapshot())
        private set

    init {
        require(workspaceRoot.isDirectory) { "Workspace root must exist" }
        require(toolchainsRoot.isDirectory) { "Toolchains root must exist" }
    }

    fun register(profile: LspServerProfile) {
        require(profile.languageId.isNotBlank() && profile.command.isNotEmpty())
        require(profile.command.none { it.isBlank() })
        check(!closed) { "Workspace manager is closed" }
        profiles[profile.languageId] = profile
        // Documents may have been opened before their toolchain finished installing.
        if (documents.values.any { it.languageId == profile.languageId }) {
            getOrStart(profile.languageId)
        }
    }

    fun openDocument(uri: String, languageId: String, version: Int, text: String): Boolean {
        if (closed || uri.isBlank() || version < 0) return false
        documents[uri] = OpenDocument(uri, languageId, version, text)
        val session = getOrStart(languageId)
        // Keep the document queued even when its language pack is not installed yet.
        return session == null || !session.initialized ||
            session.intelligence.didOpen(uri, languageId, version, text)
    }

    fun changeDocument(uri: String, version: Int, text: String): Boolean {
        val doc = documents[uri] ?: return false
        if (version <= doc.version) return false
        doc.version = version
        doc.text = text
        val session = sessions[doc.languageId] ?: return false
        return if (session.initialized) session.intelligence.didChange(uri, version, text) else true
    }

    fun closeDocument(uri: String): Boolean {
        val doc = documents.remove(uri) ?: return false
        return sessions[doc.languageId]?.takeIf { it.initialized }?.intelligence?.didClose(uri) ?: true
    }

    fun completion(uri: String, position: LspPosition, callback: (List<LspCompletionItem>) -> Unit): Boolean {
        val session = sessionFor(uri) ?: return false
        session.intelligence.completion(uri, position) { result -> main.post { callback(result) } }
        return true
    }

    fun hover(uri: String, position: LspPosition, callback: (LspHover?) -> Unit): Boolean {
        val session = sessionFor(uri) ?: return false
        session.intelligence.hover(uri, position) { result -> main.post { callback(result) } }
        return true
    }

    fun definition(uri: String, position: LspPosition, callback: (List<LspLocation>) -> Unit): Boolean {
        val session = sessionFor(uri) ?: return false
        session.intelligence.definition(uri, position) { result -> main.post { callback(result) } }
        return true
    }

    private fun sessionFor(uri: String): ServerSession? {
        val doc = documents[uri] ?: return null
        return sessions[doc.languageId]?.takeIf { it.initialized }
    }

    @Synchronized
    private fun getOrStart(languageId: String): ServerSession? {
        sessions[languageId]?.let { return it }
        val profile = profiles[languageId] ?: run {
            publish { it.copy(statusByLanguage = it.statusByLanguage + (languageId to "No server profile installed")) }
            return null
        }
        val root = toolchainsRoot.canonicalFile.toPath()
        val executable = File(profile.command.first()).canonicalFile
        require(executable.toPath().startsWith(root)) {
            "Language server executable must be inside Byte's private toolchains directory"
        }
        require(executable.isFile && executable.canExecute()) {
            "Language server executable is missing or not executable: $executable"
        }

        var intelligence: LspIntelligence? = null
        val process = LspServerProcess(
            command = listOf(executable.absolutePath) + profile.command.drop(1),
            workingDirectory = workspaceRoot.canonicalFile,
            environment = profile.environment
        ) { event ->
            when (event) {
                is LspEvent.Message -> intelligence?.acceptNotification(event.value)
                is LspEvent.Stderr -> publish { it.copy(lastServerMessage = event.line) }
                is LspEvent.Exited -> publish {
                    sessions.remove(languageId)
                    it.copy(
                        runningLanguages = it.runningLanguages - languageId,
                        statusByLanguage = it.statusByLanguage + (languageId to "Exited (${event.exitCode})")
                    )
                }
                is LspEvent.Failed -> publish {
                    it.copy(statusByLanguage = it.statusByLanguage + (languageId to "Error: ${event.reason}"))
                }
            }
        }
        if (!process.start()) return null

        val facade = LspIntelligence(
            process,
            onDiagnostics = { uri, version, diagnostics ->
                val current = documents[uri]
                if (current != null && (version == null || version >= current.version)) {
                    publish { it.copy(diagnosticsByUri = it.diagnosticsByUri + (uri to diagnostics)) }
                }
            }
        )
        intelligence = facade
        val session = ServerSession(process, facade)
        sessions[languageId] = session
        publish {
            it.copy(
                runningLanguages = it.runningLanguages + languageId,
                statusByLanguage = it.statusByLanguage + (languageId to "Starting")
            )
        }

        val params = JSONObject()
            .put("processId", android.os.Process.myPid())
            .put("rootUri", workspaceRoot.toURI().toString())
            .put("capabilities", JSONObject()
                .put("textDocument", JSONObject()
                    .put("completion", JSONObject().put("completionItem", JSONObject().put("snippetSupport", false)))
                    .put("hover", JSONObject())
                    .put("definition", JSONObject())
                    .put("publishDiagnostics", JSONObject())))
            .put("clientInfo", JSONObject().put("name", "Byte IDE").put("version", "0.1.0"))

        process.request("initialize", params) { response ->
            main.post {
                if (response.has("error")) {
                    publish { it.copy(statusByLanguage = it.statusByLanguage + (languageId to "Initialize failed")) }
                    process.terminate()
                } else {
                    session.initialized = true
                    process.notify("initialized")
                    publish { it.copy(statusByLanguage = it.statusByLanguage + (languageId to "Ready")) }
                    documents.values.filter { it.languageId == languageId }.forEach { doc ->
                        facade.didOpen(doc.uri, doc.languageId, doc.version, doc.text)
                    }
                }
            }
        }
        return session
    }

    private fun publish(transform: (LspWorkspaceSnapshot) -> LspWorkspaceSnapshot) {
        main.post {
            snapshot = transform(snapshot)
            onSnapshot(snapshot)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        documents.values.toList().forEach { doc ->
            sessions[doc.languageId]?.takeIf { it.initialized }?.intelligence?.didClose(doc.uri)
        }
        documents.clear()
        sessions.values.forEach { it.process.close() }
        sessions.clear()
        publish { LspWorkspaceSnapshot(statusByLanguage = it.statusByLanguage.mapValues { "Stopped" }) }
    }
}
