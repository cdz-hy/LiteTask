package com.litetask.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat

/**
 * 系统分享落地的解析结果载体。
 */
data class IncomingSharePayload(
    val sharedText: String? = null,
    val sharedSubject: String? = null,
    val sharedUris: List<Uri> = emptyList()
) {
    val combinedText: String
        get() = listOfNotNull(
            sharedSubject?.trim()?.takeIf { it.isNotEmpty() },
            sharedText?.trim()?.takeIf { it.isNotEmpty() }
        ).distinct().joinToString("\n")

    val isEmpty: Boolean
        get() = combinedText.isBlank() && sharedUris.isEmpty()
}

object ShareIntentParser {

    fun parseShareIntent(intent: Intent?): IncomingSharePayload? {
        if (intent == null) return null
        if (intent.getBooleanExtra(EXTRA_SHARE_CONSUMED, false)) return null

        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) {
            return null
        }

        val sharedText = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            ?.trim()?.takeIf { it.isNotEmpty() }
        val sharedSubject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
            ?.trim()?.takeIf { it.isNotEmpty() }

        val uriSet = linkedSetOf<Uri>()
        when (action) {
            Intent.ACTION_SEND -> {
                val streamUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (streamUri != null) uriSet.add(streamUri)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                streamUris?.forEach { uri -> uriSet.add(uri) }
            }
        }

        collectClipDataUris(intent.clipData, uriSet)

        val payload = IncomingSharePayload(
            sharedText = sharedText,
            sharedSubject = sharedSubject,
            sharedUris = uriSet.toList()
        )
        return payload.takeIf { !it.isEmpty }
    }

    fun markConsumed(intent: Intent?) {
        intent?.putExtra(EXTRA_SHARE_CONSUMED, true)
    }

    private fun collectClipDataUris(clipData: ClipData?, out: MutableSet<Uri>) {
        if (clipData == null) return
        for (i in 0 until clipData.itemCount) {
            val uri = clipData.getItemAt(i)?.uri
            if (uri != null) out.add(uri)
        }
    }

    fun resolveDisplayName(context: Context, uri: Uri): String {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            }
        }.getOrNull()
        if (!queried.isNullOrBlank()) return queried
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "分享文件"
    }

    fun isImageUri(context: Context, uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull().orEmpty()
        if (mime.startsWith("image/", ignoreCase = true)) return true
        val name = resolveDisplayName(context, uri).lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")
    }

    private const val EXTRA_SHARE_CONSUMED = "litetask_share_consumed"
}
