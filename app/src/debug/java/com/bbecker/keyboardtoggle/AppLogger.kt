package com.bbecker.whmkeyboardtoggle

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

object AppLogger {
    private const val INTERNAL_TAG = "KeyboardToggleLog"
    private const val FILE_NAME = "keyboard-toggle.log"
    private const val MIME_TYPE = "text/plain"
    private val LOG_DIRECTORY = "${Environment.DIRECTORY_DOWNLOADS}/KeyboardToggle/"
    private val timestampFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @Volatile
    private var cachedLogUri: Uri? = null

    fun i(context: Context, tag: String, message: String) {
        Log.i(tag, message)
        append(context, "I", tag, message)
    }

    fun w(context: Context, tag: String, message: String, error: Throwable? = null) {
        if (error == null) {
            Log.w(tag, message)
        } else {
            Log.w(tag, message, error)
        }

        append(context, "W", tag, message, error)
    }

    @Synchronized
    private fun append(context: Context, level: String, tag: String, message: String, error: Throwable? = null) {
        val uri = cachedLogUri ?: findOrCreateLogUri(context).also { cachedLogUri = it }
        if (uri == null) {
            return
        }

        val line = buildString {
            append(timestampFormatter.format(OffsetDateTime.now()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(message)

            if (error != null) {
                append(" | ")
                append(error.javaClass.simpleName)
                append(": ")
                append(error.message.orEmpty())
            }

            appendLine()
        }

        try {
            context.contentResolver.openOutputStream(uri, "wa")?.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer?.write(line)
            }
        } catch (error: Exception) {
            cachedLogUri = null
            Log.e(INTERNAL_TAG, "Failed to write to device log file.", error)
        }
    }

    private fun findOrCreateLogUri(context: Context): Uri? {
        return findExistingLogUri(context) ?: createLogUri(context)
    }

    private fun findExistingLogUri(context: Context): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(FILE_NAME, LOG_DIRECTORY)

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }

        return null
    }

    private fun createLogUri(context: Context): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, LOG_DIRECTORY)
        }

        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }
}