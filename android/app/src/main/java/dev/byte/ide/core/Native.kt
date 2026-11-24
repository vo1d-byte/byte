package dev.byte.ide.core

/** JNI surface of libbyte_jni. String results starting with "ERR:" are errors. */
object Native {
    init { System.loadLibrary("byte_jni") }

    // session / files
    external fun sessionOpen(root: String): Long
    external fun sessionClose(h: Long)
    external fun listDir(h: Long, rel: String): String

    // tabs
    external fun tabOpen(h: Long, path: String): Int
    external fun tabClose(h: Long, idx: Int)
    external fun tabActivate(h: Long, idx: Int)
    external fun tabList(h: Long): String
    external fun saveDirty(h: Long): Int

    // buffer (UTF-16 offsets)
    external fun bufText(h: Long): String
    external fun bufSelection(h: Long): String
    external fun bufSync(h: Long, text: String, anchor: Int, head: Int)
    external fun bufOp(h: Long, op: String, arg: String): String

    // terminal
    external fun termSpawn(program: String, cwd: String, rows: Int, cols: Int): Long
    external fun termWrite(h: Long, data: ByteArray)
    external fun termRead(h: Long): ByteArray?
    external fun termResize(h: Long, rows: Int, cols: Int)
    external fun termClose(h: Long)
    external fun termAlive(h: Long): Boolean

    // toolchains / git
    external fun toolchainInstall(manifestToml: String, archivePath: String, abi: String, root: String): String
    external fun gitStatus(root: String): String
    external fun gitCommit(root: String, message: String, name: String, email: String): String
    external fun gitPush(root: String, remote: String, branch: String, token: String): String
}
