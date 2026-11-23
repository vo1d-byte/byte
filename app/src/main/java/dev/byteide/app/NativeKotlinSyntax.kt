package dev.byteide.app

/** UTF-16 ranges suitable for Compose AnnotatedString styling. */
data class EditorSyntaxSpan(val start: Int, val end: Int, val kind: String)

object NativeKotlinSyntaxBridge {
    val isAvailable: Boolean get() = NativeEditorBridge.isAvailable

    @JvmStatic external fun nativeCreate(source: String): Long
    @JvmStatic external fun nativeDestroy(handle: Long)
    @JvmStatic external fun nativeReplace(handle: Long, startUtf8Byte: Int, endUtf8Byte: Int, replacement: String): Boolean
    @JvmStatic external fun nativeGetSpans(handle: Long): String
}

class NativeKotlinSyntax(initialText: String) : AutoCloseable {
    private var handle = if (isAvailable) runCatching { nativeCreate(initialText) }.getOrDefault(0L) else 0L
    val isAvailable: Boolean get() = NativeKotlinSyntaxBridge.isAvailable && handle != 0L

    fun replace(startUtf8Byte: Int, endUtf8Byte: Int, replacement: String): Boolean =
        isAvailable && runCatching {
            NativeKotlinSyntaxBridge.nativeReplace(handle, startUtf8Byte, endUtf8Byte, replacement)
        }.getOrDefault(false)

    fun spans(): List<EditorSyntaxSpan> {
        if (!isAvailable) return emptyList()
        val encoded = runCatching { NativeKotlinSyntaxBridge.nativeGetSpans(handle) }.getOrDefault("")
        return encoded.lineSequence().mapNotNull { line ->
            val parts = line.split(':', limit = 3)
            if (parts.size != 3) null else {
                val start = parts[0].toIntOrNull()
                val end = parts[1].toIntOrNull()
                if (start == null || end == null || start >= end) null
                else EditorSyntaxSpan(start, end, parts[2])
            }
        }.toList()
    }

    override fun close() {
        if (handle != 0L) {
            runCatching { NativeKotlinSyntaxBridge.nativeDestroy(handle) }
            handle = 0L
        }
    }
}
