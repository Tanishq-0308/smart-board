package com.smartboard.teach.core.util

import androidx.core.util.AtomicFile
import java.io.File
import java.io.OutputStream

/**
 * Writes via a temp file + fsync + rename, so a crash or power cut mid-write
 * leaves the previous file intact instead of a truncated one. Matters most for
 * files that are cached by existence (PDF page renders, posters): a half-written
 * JPEG would otherwise be served forever.
 */
fun File.writeAtomically(block: (OutputStream) -> Unit) {
    parentFile?.mkdirs()
    val atomic = AtomicFile(this)
    val out = atomic.startWrite()
    try {
        block(out)
        atomic.finishWrite(out)
    } catch (t: Throwable) {
        atomic.failWrite(out)
        throw t
    }
}
