package com.litetask.app.util

import com.google.gson.Gson
import com.litetask.app.data.model.ComponentType
import com.litetask.app.data.model.FileAttachmentData
import com.litetask.app.data.model.TaskComponentEntity
import com.litetask.app.data.model.VoiceNoteData
import com.litetask.app.data.model.VoiceNoteSupport
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * LiteTask 完整备份归档助手（支持 `.zip` 多媒体包 + 向后兼容旧版 `.json`）。
 *
 * `.zip` 结构：
 * - `backup.json` : 主数据库全量 JSON
 * - `attachments/<fileName>` : 受管任务附件文件
 * - `voice_notes/<fileName>` : 语音备忘录音频文件（Round 6）
 */
object BackupArchiveHelper {

    const val BACKUP_JSON_ENTRY = "backup.json"
    const val ATTACHMENTS_PREFIX = "attachments/"
    const val VOICE_NOTES_PREFIX = "voice_notes/"

    private val componentGson = Gson()

    data class ArchiveReadResult(
        val json: String,
        val extractedAttachments: Map<String, File>,
        val extractedVoiceNotes: Map<String, File>,
        val isZipArchive: Boolean
    )

    fun writeZipArchive(
        outputStream: OutputStream,
        backupJson: String,
        attachmentFiles: List<File>,
        voiceNoteFiles: List<File> = emptyList()
    ) {
        ZipOutputStream(outputStream).use { zos ->
            // 1. 优先写入 backup.json 便于流式识别
            zos.putNextEntry(ZipEntry(BACKUP_JSON_ENTRY))
            zos.write(backupJson.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            val writtenEntries = HashSet<String>()
            writeDirectoryEntries(zos, ATTACHMENTS_PREFIX, attachmentFiles, writtenEntries)
            writeDirectoryEntries(zos, VOICE_NOTES_PREFIX, voiceNoteFiles, writtenEntries)
            zos.finish()
        }
    }

    private fun writeDirectoryEntries(
        zos: ZipOutputStream,
        prefix: String,
        files: List<File>,
        writtenEntries: MutableSet<String>
    ) {
        val buffer = ByteArray(16 * 1024)
        files.forEach { file ->
            if (!file.exists() || !file.isFile || !file.canRead()) return@forEach
            val safeName = AttachmentStorageHelper.sanitizeFileName(file.name)
            val entryName = "$prefix$safeName"
            if (!writtenEntries.add(entryName)) return@forEach
            zos.putNextEntry(ZipEntry(entryName))
            FileInputStream(file).use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    zos.write(buffer, 0, read)
                }
            }
            zos.closeEntry()
        }
    }

    /**
     * 自动探测 `.zip` 与旧版 `.json` 备份流，解包附件并防护 Zip Slip。
     */
    fun readArchiveAuto(
        inputStream: InputStream,
        targetAttachmentsDir: File,
        targetVoiceNotesDir: File
    ): ArchiveReadResult {
        val pushback = PushbackInputStream(inputStream, 4)
        val header = ByteArray(4)
        val bytesRead = pushback.read(header)
        if (bytesRead > 0) {
            pushback.unread(header, 0, bytesRead)
        }

        val isZip = bytesRead >= 2 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
        if (!isZip) {
            val json = pushback.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            return ArchiveReadResult(
                json = json,
                extractedAttachments = emptyMap(),
                extractedVoiceNotes = emptyMap(),
                isZipArchive = false
            )
        }

        if (!targetAttachmentsDir.exists()) targetAttachmentsDir.mkdirs()
        if (!targetVoiceNotesDir.exists()) targetVoiceNotesDir.mkdirs()

        var backupJson: String? = null
        val extractedAttachments = LinkedHashMap<String, File>()
        val extractedVoiceNotes = LinkedHashMap<String, File>()

        ZipInputStream(pushback).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val entryName = entry.name.replace('\\', '/')
                when {
                    entryName == BACKUP_JSON_ENTRY -> {
                        backupJson = readZipEntryText(zis)
                    }
                    entryName.startsWith(ATTACHMENTS_PREFIX) -> {
                        val rawBaseName = entryName.removePrefix(ATTACHMENTS_PREFIX).substringAfterLast('/')
                        val safeName = AttachmentStorageHelper.sanitizeFileName(rawBaseName)
                        if (safeName.isNotBlank()) {
                            val outFile = extractSafeEntryFile(zis, targetAttachmentsDir, safeName)
                            if (outFile != null) {
                                extractedAttachments[rawBaseName] = outFile
                                extractedAttachments[safeName] = outFile
                            }
                        }
                    }
                    entryName.startsWith(VOICE_NOTES_PREFIX) -> {
                        val rawBaseName = entryName.removePrefix(VOICE_NOTES_PREFIX).substringAfterLast('/')
                        val safeName = AttachmentStorageHelper.sanitizeFileName(rawBaseName)
                        if (safeName.isNotBlank()) {
                            val outFile = extractSafeEntryFile(zis, targetVoiceNotesDir, safeName)
                            if (outFile != null) {
                                extractedVoiceNotes[rawBaseName] = outFile
                                extractedVoiceNotes[safeName] = outFile
                            }
                        }
                    }
                }
                zis.closeEntry()
            }
        }

        val resolvedJson = backupJson?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("压缩包内未找到有效的 $BACKUP_JSON_ENTRY")

        return ArchiveReadResult(
            json = resolvedJson,
            extractedAttachments = extractedAttachments,
            extractedVoiceNotes = extractedVoiceNotes,
            isZipArchive = true
        )
    }

    private fun readZipEntryText(zis: ZipInputStream): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = zis.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun extractSafeEntryFile(
        zis: ZipInputStream,
        targetDir: File,
        safeName: String
    ): File? {
        val dirCanonical = targetDir.canonicalFile
        val destFile = File(dirCanonical, safeName).canonicalFile
        // 严格防 Zip Slip：必须在目标目录内部
        if (!destFile.path.startsWith(dirCanonical.path + File.separator)) {
            return null
        }
        val buf = ByteArray(16 * 1024)
        FileOutputStream(destFile).use { fos ->
            while (true) {
                val n = zis.read(buf)
                if (n <= 0) break
                fos.write(buf, 0, n)
            }
            fos.flush()
        }
        return destFile
    }

    /**
     * 当跨设备或重装恢复 ZIP 备份时，将 FILE_ATTACHMENT / VOICE_NOTE 组件内的本地沙盒路径重映射到当前设备的还原目录。
     */
    fun remapRestoredComponent(
        entity: TaskComponentEntity,
        extractedAttachments: Map<String, File>,
        fileUriResolver: (File) -> String,
        extractedVoiceNotes: Map<String, File> = emptyMap()
    ): TaskComponentEntity = when (entity.type) {
        ComponentType.FILE_ATTACHMENT -> remapFileAttachment(entity, extractedAttachments, fileUriResolver)
        ComponentType.VOICE_NOTE -> remapVoiceNote(entity, extractedVoiceNotes, fileUriResolver)
        else -> entity
    }

    private fun remapFileAttachment(
        entity: TaskComponentEntity,
        extractedAttachments: Map<String, File>,
        fileUriResolver: (File) -> String
    ): TaskComponentEntity {
        if (extractedAttachments.isEmpty()) return entity
        val oldData = runCatching {
            componentGson.fromJson(entity.dataJson, FileAttachmentData::class.java)
        }.getOrNull() ?: return entity

        if (!oldData.isManagedCopy && oldData.managedFilePath.isNullOrBlank()) {
            return entity
        }
        val baseName = oldData.managedFilePath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it.replace('\\', '/')).name }
            ?: oldData.fileName.takeIf { it.isNotBlank() }
            ?: return entity

        val matchedFile = findExtractedFile(extractedAttachments, baseName) ?: return entity
        val updatedData = oldData.copy(
            fileUri = fileUriResolver(matchedFile),
            managedFilePath = matchedFile.absolutePath,
            fileSize = if (matchedFile.length() > 0L) matchedFile.length() else oldData.fileSize,
            isManagedCopy = true
        )
        return entity.copy(dataJson = componentGson.toJson(updatedData))
    }

    private fun remapVoiceNote(
        entity: TaskComponentEntity,
        extractedVoiceNotes: Map<String, File>,
        fileUriResolver: (File) -> String
    ): TaskComponentEntity {
        if (extractedVoiceNotes.isEmpty()) return entity
        val oldData = runCatching {
            componentGson.fromJson(entity.dataJson, VoiceNoteData::class.java)
        }.getOrNull() ?: return entity

        val baseName = VoiceNoteSupport.extractVoiceFileName(oldData) ?: return entity
        val matchedFile = findExtractedFile(extractedVoiceNotes, baseName) ?: return entity
        val updatedData = oldData.copy(
            filePath = matchedFile.absolutePath,
            fileUri = fileUriResolver(matchedFile)
        )
        return entity.copy(dataJson = componentGson.toJson(updatedData))
    }

    private fun findExtractedFile(map: Map<String, File>, rawBaseName: String): File? {
        map[rawBaseName]?.let { return it }
        val safeName = AttachmentStorageHelper.sanitizeFileName(rawBaseName)
        return map[safeName]
    }
}
