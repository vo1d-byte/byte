package dev.byteide.app

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

data class ToolchainPackageSpec(
    val packageId: String,
    val displayName: String,
    val languageId: String,
    val version: String,
    val artifactUrl: String,
    val sha256: String,
    val archiveFormat: String = "zip",
    val executablePaths: List<String> = emptyList(),
    val languageServerExecutable: String? = null,
    val languageServerArguments: List<String> = emptyList()
)

enum class ToolchainInstallPhase { IDLE, DOWNLOADING, VERIFYING, EXTRACTING, REGISTERING, INSTALLED, FAILED }
data class ToolchainInstallState(val packageId: String, val phase: ToolchainInstallPhase, val bytesDownloaded: Long = 0, val message: String = "")
data class InstalledToolchain(val packageId: String, val languageId: String, val version: String, val installPath: String, val installedAtEpochMs: Long)

/** Installs pinned ZIP toolchains into app-private storage. Specs must come from a reviewed catalog. */
class ToolchainInstaller(
    private val toolchainsRoot: File,
    private val cacheRoot: File,
    private val lspWorkspace: LspWorkspaceManager? = null,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    var installStates by mutableStateOf<Map<String, ToolchainInstallState>>(emptyMap())
        private set
    var installedToolchains by mutableStateOf<List<InstalledToolchain>>(readInstalledRegistry())
        private set

    init {
        toolchainsRoot.mkdirs()
        cacheRoot.mkdirs()
        require(toolchainsRoot.isDirectory && cacheRoot.isDirectory) { "Toolchain storage unavailable" }
    }

    fun install(spec: ToolchainPackageSpec, callback: (Result<InstalledToolchain>) -> Unit = {}) {
        validateSpec(spec)
        check(!closed) { "Toolchain installer is closed" }
        setState(spec.packageId, ToolchainInstallPhase.DOWNLOADING, "Downloading ${spec.displayName}")
        executor.execute {
            val result = runCatching { installBlocking(spec) }
            main.post {
                result.onSuccess { installed ->
                    installedToolchains = readInstalledRegistry()
                    setState(spec.packageId, ToolchainInstallPhase.INSTALLED, "Installed ${spec.version}")
                    callback(Result.success(installed))
                }.onFailure { error ->
                    setState(spec.packageId, ToolchainInstallPhase.FAILED, error.message ?: "Installation failed")
                    callback(Result.failure(error))
                }
            }
        }
    }

    private fun installBlocking(spec: ToolchainPackageSpec): InstalledToolchain {
        val packageDir = File(toolchainsRoot, safeSegment(spec.packageId))
        val versionDir = File(packageDir, safeSegment(spec.version))
        val staging = File(packageDir, ".staging-${System.nanoTime()}")
        val archive = File(cacheRoot, "${safeSegment(spec.packageId)}-${safeSegment(spec.version)}.zip.part")
        packageDir.mkdirs()
        try {
            download(spec, archive)
            updateStateFromWorker(spec.packageId, ToolchainInstallPhase.VERIFYING, "Verifying SHA-256")
            val actual = sha256(archive)
            require(actual.equals(spec.sha256, ignoreCase = true)) { "SHA-256 mismatch: expected ${spec.sha256}, received $actual" }
            updateStateFromWorker(spec.packageId, ToolchainInstallPhase.EXTRACTING, "Extracting package")
            require(spec.archiveFormat == "zip") { "Unsupported archive format: ${spec.archiveFormat}" }
            require(!versionDir.exists()) { "Version is already installed: ${spec.version}" }
            staging.mkdirs()
            extractZipSafely(archive, staging)
            spec.executablePaths.forEach { relative ->
                val executable = safeChild(staging, relative)
                require(executable.isFile) { "Declared executable is missing: $relative" }
                check(executable.setExecutable(true, true) || executable.canExecute()) { "Unable to mark executable: $relative" }
            }
            require(versionDir.canonicalFile.toPath().startsWith(packageDir.canonicalFile.toPath())) { "Install destination escaped toolchains directory" }
            check(staging.renameTo(versionDir)) { "Unable to publish installed toolchain" }
            val installed = InstalledToolchain(spec.packageId, spec.languageId, spec.version, versionDir.canonicalPath, System.currentTimeMillis())
            writeInstalledRegistry(installed)
            spec.languageServerExecutable?.let { relative ->
                val executable = safeChild(versionDir, relative)
                require(executable.isFile && executable.canExecute()) { "Language server missing or not executable: $relative" }
                updateStateFromWorker(spec.packageId, ToolchainInstallPhase.REGISTERING, "Registering language server")
                lspWorkspace?.register(LspServerProfile(spec.languageId, listOf(executable.canonicalPath) + spec.languageServerArguments))
            }
            return installed
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        } finally {
            archive.delete()
        }
    }

    private fun download(spec: ToolchainPackageSpec, destination: File) {
        val connection = (URL(spec.artifactUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "ByteIDE-ToolchainManager/0.1")
        }
        try {
            connection.connect()
            require(connection.responseCode in 200..299) { "Download failed with HTTP ${connection.responseCode}" }
            require(connection.url.protocol.equals("https", true)) { "Insecure redirect rejected" }
            require(connection.contentLengthLong <= MAX_ARCHIVE_BYTES) { "Archive exceeds download size limit" }
            var total = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ARCHIVE_BYTES) { "Archive exceeds download size limit" }
                        output.write(buffer, 0, count)
                        updateStateFromWorker(spec.packageId, ToolchainInstallPhase.DOWNLOADING, "Downloaded $total bytes", total)
                    }
                    output.fd.sync()
                }
            }
            require(total > 0) { "Downloaded archive is empty" }
        } finally { connection.disconnect() }
    }

    private fun extractZipSafely(archive: File, destination: File) {
        var expanded = 0L
        var entries = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                require(entries <= MAX_ENTRIES) { "Archive contains too many entries" }
                val output = safeChild(destination, entry.name)
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            expanded += count
                            require(expanded <= MAX_EXPANDED_BYTES) { "Expanded archive exceeds size limit" }
                            stream.write(buffer, 0, count)
                        }
                        stream.fd.sync()
                    }
                }
                zip.closeEntry()
            }
        }
        require(entries > 0) { "Archive contains no entries" }
    }

    private fun safeChild(root: File, relative: String): File {
        require(relative.isNotBlank() && !relative.startsWith("/") && !relative.startsWith("\\\\")) { "Invalid archive path" }
        // ZIP directory entries conventionally end in '/'; allow that trailing separator only.
        val normalized = relative.replace('\\\\', '/').trimEnd('/')
        require(normalized.isNotBlank())
        require(normalized.split('/').none { it == ".." || it.isBlank() || it == "." }) { "Path traversal rejected" }
        val child = File(root, normalized).canonicalFile
        require(child.toPath().startsWith(root.canonicalFile.toPath())) { "Path escaped installation directory" }
        return child
    }

    private fun validateSpec(spec: ToolchainPackageSpec) {
        safeSegment(spec.packageId)
        safeSegment(spec.version)
        require(spec.displayName.isNotBlank() && spec.languageId.isNotBlank()) { "Package metadata incomplete" }
        require(spec.sha256.matches(Regex("(?i)[a-f0-9]{64}"))) { "Pinned SHA-256 required" }
        require(URL(spec.artifactUrl).protocol.equals("https", true)) { "HTTPS artifact URL required" }
        require(spec.archiveFormat == "zip") { "Only ZIP archives supported" }
        spec.executablePaths.forEach { require(safeRelativePath(it)) { "Invalid executable path: $it" } }
        spec.languageServerExecutable?.let { require(safeRelativePath(it)) { "Invalid language server path" } }
        require(spec.languageServerArguments.none { it.contains('\u0000') }) { "Invalid server argument" }
    }

    private fun safeSegment(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}"))) { "Invalid path segment: $value" }
        return value
    }

    private fun safeRelativePath(value: String): Boolean =
        value.isNotBlank() && !value.startsWith("/") &&
            value.replace('\\', '/').split('/').all { it.isNotBlank() && it != "." && it != ".." }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun readInstalledRegistry(): List<InstalledToolchain> {
        val file = File(toolchainsRoot, "installed.json")
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                InstalledToolchain(item.getString("packageId"), item.getString("languageId"), item.getString("version"), item.getString("installPath"), item.getLong("installedAtEpochMs"))
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun writeInstalledRegistry(installed: InstalledToolchain) {
        val records = readInstalledRegistry().filterNot { it.packageId == installed.packageId && it.version == installed.version } + installed
        val array = JSONArray()
        records.forEach { item ->
            array.put(JSONObject().put("packageId", item.packageId).put("languageId", item.languageId)
                .put("version", item.version).put("installPath", item.installPath).put("installedAtEpochMs", item.installedAtEpochMs))
        }
        val temp = File(toolchainsRoot, "installed.json.tmp")
        temp.writeText(array.toString())
        check(temp.renameTo(File(toolchainsRoot, "installed.json"))) { "Unable to update toolchain registry" }
    }

    private fun updateStateFromWorker(id: String, phase: ToolchainInstallPhase, message: String, bytes: Long = 0) {
        main.post { setState(id, phase, message, bytes) }
    }

    private fun setState(id: String, phase: ToolchainInstallPhase, message: String, bytes: Long = 0) {
        installStates = installStates + (id to ToolchainInstallState(id, phase, bytes, message))
    }

    override fun close() { closed = true; executor.shutdownNow() }

    companion object {
        private const val MAX_ARCHIVE_BYTES = 1_500L * 1024 * 1024
        private const val MAX_EXPANDED_BYTES = 3_000L * 1024 * 1024
        private const val MAX_ENTRIES = 100_000
    }
}
