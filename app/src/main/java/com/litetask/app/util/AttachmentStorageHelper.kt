package com.litetask.app.util

import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import com.litetask.app.data.model.FileAttachmentData
import com.litetask.app.data.model.VoiceNoteData
import com.litetask.app.data.model.VoiceNoteSupport
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID

/**
 * 任务文件附件的本地沙盒托管、URI 解析、失效检测与分享助手。
 *
 * 存储策略：
 * - `<= 20 MB` 的附件自动拷贝至应用私有目录 `filesDir/attachments/`，生成持久化 `FileProvider` URI，
 *   彻底避免系统重启或清理授权后 SAF `content://` 权限失效导致的“文件打不开”问题；
 * - `> 20 MB` 的大文件仅申请持久化只读 URI 权限并保存原 URI，在卡片与详情中提供实时有效性检测与一键重连。
 */
object AttachmentStorageHelper {

    const val MANAGED_COPY_LIMIT_BYTES: Long = 20L * 1024L * 1024L
    const val ATTACHMENTS_DIR_NAME = "attachments"
    const val VOICE_NOTES_DIR_NAME = "voice_notes"
    const val CAMERA_TEMP_DIR_NAME = "camera_temp"

    data class UriFileMeta(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    sealed class CopyResult {
        data class Copied(val bytesCopied: Long) : CopyResult()
        data class TooLarge(val bytesRead: Long) : CopyResult()
        data class Failed(val error: Throwable) : CopyResult()
    }

    fun attachmentsDir(context: Context): File =
        File(context.filesDir, ATTACHMENTS_DIR_NAME).apply { if (!exists()) mkdirs() }

    fun voiceNotesDir(context: Context): File =
        File(context.filesDir, VOICE_NOTES_DIR_NAME).apply { if (!exists()) mkdirs() }

    fun cameraTempDir(context: Context): File =
        File(context.cacheDir, CAMERA_TEMP_DIR_NAME).apply { if (!exists()) mkdirs() }

    fun sanitizeFileName(rawName: String, fallbackExt: String? = null): String {
        val trimmed = rawName.trim().substringAfterLast('/').substringAfterLast('\\')
        val cleaned = trimmed.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_")
            .trim('.', ' ')
        val base = cleaned.ifBlank { "attachment_${System.currentTimeMillis()}" }
        val finalName = if (!base.contains('.') && !fallbackExt.isNullOrBlank()) {
            "$base.${fallbackExt.trimStart('.')}"
        } else {
            base
        }
        return finalName.take(96)
    }

    fun inferMimeType(fileName: String, fallbackMime: String? = null): String {
        val candidate = fallbackMime?.trim()?.takeIf { it.isNotBlank() && it != "*/*" && it != "application/octet-stream" }
        if (candidate != null) return candidate

        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isNotBlank()) {
            EXT_TO_MIME[ext]?.let { return it }
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return fallbackMime?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
    }

    fun shouldManagedCopy(fileSizeBytes: Long): Boolean =
        fileSizeBytes in 0L..MANAGED_COPY_LIMIT_BYTES

    fun copyStreamWithByteLimit(
        input: InputStream,
        destFile: File,
        maxBytes: Long = MANAGED_COPY_LIMIT_BYTES
    ): CopyResult {
        return try {
            destFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            var total = 0L
            var exceeded = false
            val buffer = ByteArray(16 * 1024)
            FileOutputStream(destFile).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        exceeded = true
                        break
                    }
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
            if (exceeded) {
                runCatching { destFile.delete() }
                return CopyResult.TooLarge(total)
            }
            CopyResult.Copied(total)
        } catch (t: Throwable) {
            runCatching { destFile.delete() }
            CopyResult.Failed(t)
        }
    }

    fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

    fun fileProviderUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, fileProviderAuthority(context), file)

    fun createTempCameraImageUri(context: Context): Pair<Uri, File> {
        val tempFile = File(
            cameraTempDir(context),
            "cam_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        )
        return fileProviderUri(context, tempFile) to tempFile
    }

    fun queryUriMetadata(context: Context, uri: Uri): UriFileMeta {
        var displayName: String? = null
        var fileSize = -1L

        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { path ->
                val f = File(path)
                displayName = f.name
                fileSize = if (f.exists()) f.length() else -1L
            }
        } else {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                            fileSize = cursor.getLong(sizeIdx)
                        }
                    }
                }
            }
        }

        val rawMime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val extFromMime = rawMime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        val safeName = sanitizeFileName(
            displayName ?: uri.lastPathSegment ?: "attachment",
            fallbackExt = extFromMime
        )
        val mime = inferMimeType(safeName, rawMime)
        return UriFileMeta(
            fileName = safeName,
            mimeType = mime,
            sizeBytes = fileSize.coerceAtLeast(0L)
        )
    }

    fun tryTakePersistableReadPermission(context: Context, uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    /**
     * 导入任意系统 URI（文档/相册/临时拍照文件）。
     * - 当实际体积 `<= 20 MB` 时，在本地 `filesDir/attachments/` 创建受管副本并签发 `FileProvider` URI；
     * - 当超出 20 MB 时保留外部引用并持久化 SAF 权限。
     */
    fun importFromUri(
        context: Context,
        uri: Uri,
        existingAlias: String? = null,
        forcedFileName: String? = null
    ): FileAttachmentData? {
        val meta = queryUriMetadata(context, uri)
        val finalName = forcedFileName?.let { sanitizeFileName(it) } ?: meta.fileName
        val finalMime = inferMimeType(finalName, meta.mimeType)

        if (meta.sizeBytes <= MANAGED_COPY_LIMIT_BYTES) {
            val destFile = File(
                attachmentsDir(context),
                "${UUID.randomUUID().toString().take(8)}_$finalName"
            )
            val copyOutcome = runCatching {
                openInputStreamForUri(context, uri)?.use { input ->
                    copyStreamWithByteLimit(input, destFile, MANAGED_COPY_LIMIT_BYTES)
                }
            }.getOrNull()

            when (copyOutcome) {
                is CopyResult.Copied -> {
                    val fpUri = fileProviderUri(context, destFile).toString()
                    return FileAttachmentData(
                        fileName = finalName,
                        fileUri = fpUri,
                        mimeType = finalMime,
                        fileSize = copyOutcome.bytesCopied,
                        managedFilePath = destFile.absolutePath,
                        displayAlias = existingAlias?.trim()?.takeIf { it.isNotEmpty() },
                        isManagedCopy = true
                    )
                }
                is CopyResult.TooLarge -> {
                    tryTakePersistableReadPermission(context, uri)
                    return FileAttachmentData(
                        fileName = finalName,
                        fileUri = uri.toString(),
                        mimeType = finalMime,
                        fileSize = if (meta.sizeBytes > 0L) meta.sizeBytes else copyOutcome.bytesRead,
                        managedFilePath = null,
                        displayAlias = existingAlias?.trim()?.takeIf { it.isNotEmpty() },
                        isManagedCopy = false
                    )
                }
                else -> {
                    runCatching { destFile.delete() }
                }
            }
        }

        // > 20 MB 或复制降级：仅保留持久化只读引用
        if (uri.scheme == ContentResolver.SCHEME_FILE) return null
        tryTakePersistableReadPermission(context, uri)
        return FileAttachmentData(
            fileName = finalName,
            fileUri = uri.toString(),
            mimeType = finalMime,
            fileSize = meta.sizeBytes,
            managedFilePath = null,
            displayAlias = existingAlias?.trim()?.takeIf { it.isNotEmpty() },
            isManagedCopy = false
        )
    }

    fun openInputStreamForUri(context: Context, uri: Uri): InputStream? {
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let { path ->
                val file = File(path)
                if (file.exists() && file.canRead()) FileInputStream(file) else null
            }
            else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        }
    }

    fun openInputStream(context: Context, data: FileAttachmentData): InputStream? {
        data.managedFilePath?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.canRead()) {
                return runCatching { FileInputStream(file) }.getOrNull()
            }
        }
        return runCatching {
            val uri = Uri.parse(data.fileUri)
            openInputStreamForUri(context, uri)
        }.getOrNull()
    }

    fun isFileAccessible(context: Context, data: FileAttachmentData): Boolean {
        data.managedFilePath?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.canRead()) {
                return true
            }
        }
        return runCatching {
            val uri = Uri.parse(data.fileUri)
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                uri.path?.let { File(it).let { f -> f.exists() && f.canRead() } } ?: false
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }
        }.getOrDefault(false)
    }

    fun resolveShareableUri(context: Context, data: FileAttachmentData): Uri {
        data.managedFilePath?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile) {
                return runCatching { fileProviderUri(context, file) }
                    .getOrElse { Uri.parse(data.fileUri) }
            }
        }
        return Uri.parse(data.fileUri)
    }

    fun openFileExternally(context: Context, data: FileAttachmentData) {
        try {
            val shareUri = resolveShareableUri(context, data)
            val mime = inferMimeType(data.fileName, data.mimeType).ifBlank { "*/*" }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(shareUri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "未找到可打开此文件类型的应用", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAttachment(context: Context, data: FileAttachmentData) {
        if (!isFileAccessible(context, data)) {
            Toast.makeText(context, "源文件已失效，请先点击重连", Toast.LENGTH_SHORT).show()
            return
        }
        val shareUri = resolveShareableUri(context, data)
        val mime = inferMimeType(data.fileName, data.mimeType).ifBlank { "*/*" }
        launchShareIntent(
            context = context,
            shareUri = shareUri,
            mime = mime,
            subject = data.effectiveDisplayName(),
            chooserTitle = "分享附件：${data.effectiveDisplayName()}"
        )
    }

    fun createVoiceNoteFile(context: Context): File =
        File(voiceNotesDir(context), "vn_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.m4a")

    fun resolveVoiceNoteShareableUri(context: Context, data: VoiceNoteData): Uri? {
        data.filePath.trim().takeIf { it.isNotEmpty() }?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile) {
                return runCatching { fileProviderUri(context, file) }.getOrNull() ?: Uri.fromFile(file)
            }
        }
        return data.fileUri?.trim()?.takeIf { it.isNotEmpty() }?.let { Uri.parse(it) }
    }

    fun isVoiceNoteAccessible(context: Context, data: VoiceNoteData): Boolean {
        data.filePath.trim().takeIf { it.isNotEmpty() }?.let { path ->
            val file = File(path)
            if (file.exists() && file.isFile && file.canRead() && file.length() > 0L) {
                return true
            }
        }
        return data.fileUri?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            runCatching {
                val uri = Uri.parse(raw)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
        } ?: false
    }

    fun shareVoiceNote(context: Context, data: VoiceNoteData) {
        if (!isVoiceNoteAccessible(context, data)) {
            Toast.makeText(context, "语音文件不可用，无法分享", Toast.LENGTH_SHORT).show()
            return
        }
        val shareUri = resolveVoiceNoteShareableUri(context, data)
        if (shareUri == null) {
            Toast.makeText(context, "未找到语音文件", Toast.LENGTH_SHORT).show()
            return
        }
        val title = data.label?.trim()?.takeIf { it.isNotEmpty() }
            ?: "语音备忘_${VoiceNoteSupport.formatDurationClock(data.durationMs)}"
        launchShareIntent(
            context = context,
            shareUri = shareUri,
            mime = "audio/mp4",
            subject = title,
            chooserTitle = "分享语音备忘：$title"
        )
    }

    private fun launchShareIntent(
        context: Context,
        shareUri: Uri,
        mime: String,
        subject: String,
        chooserTitle: String
    ) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                clipData = ClipData.newRawUri(subject, shareUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            Toast.makeText(context, "暂无可用分享目标", Toast.LENGTH_SHORT).show()
        }
    }

    fun relinkAttachment(
        context: Context,
        oldData: FileAttachmentData,
        newUri: Uri
    ): FileAttachmentData? {
        val imported = importFromUri(
            context = context,
            uri = newUri,
            existingAlias = oldData.displayAlias
        ) ?: return null
        if (oldData.managedFilePath != imported.managedFilePath) {
            deleteManagedFileIfOwned(context, oldData)
        }
        return imported
    }

    /**
     * 安全清理应用受管附件文件（仅当路径严格位于 `filesDir/attachments/` 内时才允许删除）。
     */
    fun deleteManagedFileIfOwned(context: Context, data: FileAttachmentData): Boolean =
        deleteOwnedPathIfInside(attachmentsDir(context), data.managedFilePath)

    /**
     * 安全清理应用受管语音备忘文件（仅当路径严格位于 `filesDir/voice_notes/` 内时才允许删除）。
     */
    fun deleteManagedVoiceNoteIfOwned(context: Context, data: VoiceNoteData): Boolean =
        deleteOwnedPathIfInside(voiceNotesDir(context), data.filePath)

    private fun deleteOwnedPathIfInside(baseDir: File, rawPath: String?): Boolean {
        val cleaned = rawPath?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return runCatching {
            val baseCanonical = baseDir.canonicalFile
            val targetCanonical = File(cleaned).canonicalFile
            if (targetCanonical.path.startsWith(baseCanonical.path + File.separator) &&
                targetCanonical.exists() &&
                targetCanonical.isFile
            ) {
                targetCanonical.delete()
            } else {
                false
            }
        }.getOrDefault(false)
    }

    /**
     * 若原 URI 是本应用 `cacheDir/camera_temp/` 生成的拍照临时文件，导入托管目录后顺手删除临时原片。
     */
    fun cleanupTempCameraFileIfMatches(context: Context, uri: Uri) {
        val baseDir = cameraTempDir(context)
        val segment = uri.lastPathSegment ?: return
        val candidate = File(baseDir, segment)
        if (candidate.exists() && candidate.isFile) {
            runCatching { candidate.delete() }
        }
    }

    private val EXT_TO_MIME = mapOf(
        "txt" to "text/plain",
        "md" to "text/markdown",
        "markdown" to "text/markdown",
        "csv" to "text/csv",
        "tsv" to "text/tab-separated-values",
        "json" to "application/json",
        "log" to "text/plain",
        "xml" to "application/xml",
        "yaml" to "text/yaml",
        "yml" to "text/yaml",
        "pdf" to "application/pdf",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "webp" to "image/webp",
        "gif" to "image/gif",
        "bmp" to "image/bmp",
        "m4a" to "audio/mp4",
        "mp3" to "audio/mpeg",
        "wav" to "audio/wav"
    )
}
