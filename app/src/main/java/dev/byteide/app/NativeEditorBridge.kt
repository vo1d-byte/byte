package dev.byteide.app

/**
 * Thin JNI surface for the Rust text document engine.
 *
 * The native library is optional during early UI development. Callers must check
 * [isAvailable] and keep a Kotlin-side fallback until Android ABI packaging lands.
 */
object NativeEditorBridge {
    val isAvailable: Boolean = runCatching { System.loadLibrary("byte_core") }.isSuccess

    @JvmStatic external fun nativeCreateDocument(initialText: String): Long
    @JvmStatic external fun nativeDestroyDocument(handle: Long)
    @JvmStatic external fun nativeGetText(handle: Long): String
    @JvmStatic external fun nativeReplace(handle: Long, start: Int, end: Int, replacement: String): Boolean
    @JvmStatic external fun nativeSetSelection(handle: Long, anchor: Int, head: Int): Boolean
    @JvmStatic external fun nativeUndo(handle: Long): String
    @JvmStatic external fun nativeRedo(handle: Long): String
}

/** Safe Kotlin owner for a native document handle. */
class NativeEditorDocument(initialText: String) : AutoCloseable {
    private var handle: Long = if (NativeEditorBridge.isAvailable) {
        NativeEditorBridge.nativeCreateDocument(initialText)
    } else 0L

    val isNative: Boolean get() = handle != 0L

    fun text(fallback: String): String =
        if (isNative) NativeEditorBridge.nativeGetText(handle) else fallback

    fun replace(startCodePoint: Int, endCodePoint: Int, value: String): Boolean =
        isNative && NativeEditorBridge.nativeReplace(handle, startCodePoint, endCodePoint, value)

    fun setSelection(anchorCodePoint: Int, headCodePoint: Int) {
        if (isNative) NativeEditorBridge.nativeSetSelection(handle, anchorCodePoint, headCodePoint)
    }

    fun undo(fallback: String): String =
        if (isNative) NativeEditorBridge.nativeUndo(handle) else fallback

    fun redo(fallback: String): String =
        if (isNative) NativeEditorBridge.nativeRedo(handle) else fallback

    override fun close() {
        if (handle != 0L) {
            NativeEditorBridge.nativeDestroyDocument(handle)
            handle = 0L
        }
    }
}
