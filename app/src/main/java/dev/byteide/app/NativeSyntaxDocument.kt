package dev.byteide.app

data class EditorSyntaxSpan(val start: Int, val end: Int, val kind: String)
data class EditorSyntaxDiagnostic(val start: Int, val end: Int, val message: String)

object NativeSyntaxBridge {
    val isAvailable: Boolean get() = NativeEditorBridge.isAvailable
    @JvmStatic external fun nativeCreate(path: String, source: String): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeReplace(handle: Long, startUtf8Byte: Int, endUtf8Byte: Int, replacement: String): Boolean
    @JvmStatic external fun nativeGetSpans(handle: Long): String
    @JvmStatic external fun nativeGetDiagnostics(handle: Long): String
}

class NativeSyntaxDocument(path: String, initialText: String) : AutoCloseable {
    private var handle = if (NativeSyntaxBridge.isAvailable) runCatching { NativeSyntaxBridge.nativeCreate(path, initialText) }.getOrDefault(0L) else 0L
    val isAvailable: Boolean get() = NativeSyntaxBridge.isAvailable && handle != 0L
    fun replace(startUtf8Byte: Int, endUtf8Byte: Int, replacement: String): Boolean =
        isAvailable && runCatching { NativeSyntaxBridge.nativeReplace(handle, startUtf8Byte, endUtf8Byte, replacement) }.getOrDefault(false)
    fun spans(): List<EditorSyntaxSpan> {
        if (!isAvailable) return emptyList()
        return runCatching { NativeSyntaxBridge.nativeGetSpans(handle) }.getOrDefault("").lineSequence().mapNotNull { line ->
            val p = line.split(':', limit = 3); if (p.size != 3) null else {
                val a = p[0].toIntOrNull(); val b = p[1].toIntOrNull()
                if (a == null || b == null || a >= b) null else EditorSyntaxSpan(a, b, p[2])
            }
        }.toList()
    }
    fun diagnostics(): List<EditorSyntaxDiagnostic> {
        if (!isAvailable) return emptyList()
        return runCatching { NativeSyntaxBridge.nativeGetDiagnostics(handle) }.getOrDefault("").lineSequence().mapNotNull { line ->
            val p = line.split(':', limit = 3); if (p.size != 3) null else {
                val a = p[0].toIntOrNull(); val b = p[1].toIntOrNull()
                if (a == null || b == null) null else EditorSyntaxDiagnostic(a, b, p[2])
            }
        }.toList()
    }
    override fun close() { if (handle != 0L) { runCatching { NativeSyntaxBridge.nativeDestroy(handle) }; handle = 0L } }
}
