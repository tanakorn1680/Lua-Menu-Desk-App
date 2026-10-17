package com.luamenudesk

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore

/** Writes a file into Download/<folder>/ through MediaStore (no storage permission needed, minSdk 29). */
object FileSaver {

    private val BAD_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    private fun cleanName(raw: String): String {
        val n = raw.replace(BAD_CHARS, "_").trim().trim('.')
        return if (n.isEmpty()) "script.lua" else n
    }

    private fun cleanFolder(raw: String): String =
        raw.replace('\\', '/')
            .split('/')
            .map { it.replace(BAD_CHARS, "_").trim() }
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")

    /** Returns the user-visible path, e.g. "Download/GG/Menu.lua". Throws on failure. */
    fun save(context: Context, filename: String, bytes: ByteArray, folder: String): String {
        val name = cleanName(filename)
        val sub = cleanFolder(folder)
        val relative = if (sub.isEmpty()) "Download/" else "Download/$sub/"
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        // Same name already written by this app -> overwrite it instead of creating "name (1).lua"
        var uri = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(relative, name),
            null
        )?.use { c ->
            if (c.moveToFirst()) android.content.ContentUris.withAppendedId(collection, c.getLong(0)) else null
        }

        if (uri == null) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
            }
            uri = resolver.insert(collection, values) ?: throw IllegalStateException("insert failed")
        }

        val out = resolver.openOutputStream(uri, "wt") ?: throw IllegalStateException("cannot open output")
        out.use { it.write(bytes) }
        return relative + name
    }
}
