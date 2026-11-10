package com.litetask.app.util

import com.google.gson.Gson
import com.litetask.app.data.ai.SubTaskGenerationContext
import com.litetask.app.data.ai.SubTaskGenerationSupport
import com.litetask.app.data.model.ComponentType
import com.litetask.app.data.model.FileAttachmentData
import com.litetask.app.data.model.Task
import com.litetask.app.data.model.TaskComponent
import com.litetask.app.data.model.TaskComponentEntity
import com.litetask.app.data.model.TaskComponentRegistry
import com.litetask.app.data.model.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AttachmentAndBackupSupportTest {

    private val gson = Gson()

    @Test
    fun legacyFileAttachmentJsonDeserializesSafely() {
        val legacyJson = """{"a":"report.pdf","b":"content://docs/1","c":"application/pdf","d":2048}"""
        val entity = TaskComponentEntity(
            id = 5L,
            taskId = 10L,
            type = ComponentType.FILE_ATTACHMENT,
            dataJson = legacyJson
        )
        val component = entity.toTaskComponent() as TaskComponent.FileComponent
        assertEquals("report.pdf", component.data.fileName)
        assertEquals("report.pdf", component.data.effectiveDisplayName())
        assertEquals(null, component.data.managedFilePath)
        assertFalse(component.data.isManagedCopy)

        val aliased = component.copy(data = component.data.copy(displayAlias = "体检报告"))
        assertEquals("体检报告", aliased.data.effectiveDisplayName())
        assertEquals("体检报告", TaskComponentRegistry.chipSummary(aliased))
        val searchable = TaskComponentRegistry.searchableText(aliased)
        assertTrue(searchable.contains("体检报告"))
        assertTrue(searchable.contains("report.pdf"))
    }

    @Test
    fun streamCopyRespectsManagedCopyThreshold() {
        assertTrue(AttachmentStorageHelper.shouldManagedCopy(AttachmentStorageHelper.MANAGED_COPY_LIMIT_BYTES))
        assertFalse(AttachmentStorageHelper.shouldManagedCopy(AttachmentStorageHelper.MANAGED_COPY_LIMIT_BYTES + 1L))
        assertEquals("hello_world_.txt", AttachmentStorageHelper.sanitizeFileName("dir/hello:world?.txt"))

        val tempDir = Files.createTempDirectory("litetask_copy_test").toFile()
        try {
            val okFile = File(tempDir, "ok.bin")
            val okResult = AttachmentStorageHelper.copyStreamWithByteLimit(
                ByteArrayInputStream(ByteArray(128)),
                okFile,
                maxBytes = 256
            )
            assertTrue(okResult is AttachmentStorageHelper.CopyResult.Copied)
            assertTrue(okFile.exists())
            assertEquals(128L, okFile.length())

            val bigFile = File(tempDir, "big.bin")
            val bigResult = AttachmentStorageHelper.copyStreamWithByteLimit(
                ByteArrayInputStream(ByteArray(300)),
                bigFile,
                maxBytes = 256
            )
            assertTrue(bigResult is AttachmentStorageHelper.CopyResult.TooLarge)
            assertFalse(bigFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun subTaskPromptIncludesAttachmentExcerpts() {
        val now = 1_763_200_000_000L
        val task = Task(
            id = 1L,
            title = "整理项目需求文档",
            type = TaskType.WORK,
            startTime = now,
            deadline = now + 24 * 3600_000L
        )
        val prompt = SubTaskGenerationSupport.buildSystemPrompt(
            context = SubTaskGenerationContext(
                task = task,
                attachmentNames = listOf("产品需求清单"),
                attachmentTextExcerpts = listOf("产品需求清单" to "1. 登录态刷新\n2. 附件压缩包备份")
            ),
            nowMillis = now
        )
        assertTrue(prompt.contains("关联文档正文摘要"))
        assertTrue(prompt.contains("产品需求清单"))
        assertTrue(prompt.contains("附件压缩包备份"))
    }

    @Test
    fun zipBackupRoundTripAndLegacyJsonCompatibility() {
        val srcDir = Files.createTempDirectory("litetask_zip_src").toFile()
        val dstAttach = Files.createTempDirectory("litetask_zip_dst_att").toFile()
        val dstVoice = Files.createTempDirectory("litetask_zip_dst_voice").toFile()
        try {
            val managedFile = File(srcDir, "abcd1234_notes.txt").apply {
                writeText("待办核对清单内容", StandardCharsets.UTF_8)
            }
            val managedEntity = TaskComponentEntity(
                id = 1L,
                taskId = 2L,
                type = ComponentType.FILE_ATTACHMENT,
                dataJson = gson.toJson(
                    FileAttachmentData(
                        fileName = "notes.txt",
                        fileUri = "content://old.pkg.fileprovider/managed_attachments/abcd1234_notes.txt",
                        mimeType = "text/plain",
                        fileSize = managedFile.length(),
                        managedFilePath = managedFile.absolutePath,
                        displayAlias = "会议纪要",
                        isManagedCopy = true
                    )
                )
            )
            val externalEntity = TaskComponentEntity(
                id = 2L,
                taskId = 2L,
                type = ComponentType.FILE_ATTACHMENT,
                dataJson = gson.toJson(
                    FileAttachmentData(
                        fileName = "movie.mp4",
                        fileUri = "content://external/storage/999",
                        mimeType = "video/mp4",
                        fileSize = 50L * 1024L * 1024L,
                        managedFilePath = null,
                        isManagedCopy = false
                    )
                )
            )

            val sampleJson = """{"version":1,"tasks":[],"taskComponents":[]}"""
            val zipOut = ByteArrayOutputStream()
            BackupArchiveHelper.writeZipArchive(
                outputStream = zipOut,
                backupJson = sampleJson,
                attachmentFiles = listOf(managedFile)
            )

            val readZip = BackupArchiveHelper.readArchiveAuto(
                inputStream = ByteArrayInputStream(zipOut.toByteArray()),
                targetAttachmentsDir = dstAttach,
                targetVoiceNotesDir = dstVoice
            )
            assertTrue(readZip.isZipArchive)
            assertEquals(sampleJson, readZip.json)
            val restoredFile = readZip.extractedAttachments["abcd1234_notes.txt"]!!
            assertTrue(restoredFile.exists())
            assertEquals("待办核对清单内容", restoredFile.readText(StandardCharsets.UTF_8))

            val remapped = BackupArchiveHelper.remapRestoredComponent(
                entity = managedEntity,
                extractedAttachments = readZip.extractedAttachments,
                fileUriResolver = { f -> "content://new.pkg.fileprovider/managed_attachments/${f.name}" }
            )
            val remappedData = gson.fromJson(remapped.dataJson, FileAttachmentData::class.java)
            assertEquals(restoredFile.absolutePath, remappedData.managedFilePath)
            assertEquals(
                "content://new.pkg.fileprovider/managed_attachments/${restoredFile.name}",
                remappedData.fileUri
            )
            assertEquals("会议纪要", remappedData.displayAlias)
            assertTrue(remappedData.isManagedCopy)

            // 外部大图未托管，不应被误改写
            val unchanged = BackupArchiveHelper.remapRestoredComponent(
                entity = externalEntity,
                extractedAttachments = readZip.extractedAttachments,
                fileUriResolver = { "should_not_use" }
            )
            assertEquals(externalEntity.dataJson, unchanged.dataJson)

            // 旧版纯 JSON 备份自动识别兼容性
            val legacyRead = BackupArchiveHelper.readArchiveAuto(
                inputStream = ByteArrayInputStream(sampleJson.toByteArray(StandardCharsets.UTF_8)),
                targetAttachmentsDir = dstAttach,
                targetVoiceNotesDir = dstVoice
            )
            assertFalse(legacyRead.isZipArchive)
            assertEquals(sampleJson, legacyRead.json)
        } finally {
            srcDir.deleteRecursively()
            dstAttach.deleteRecursively()
            dstVoice.deleteRecursively()
        }
    }

    @Test
    fun zipImportFlattensTraversalNamesSafely() {
        val dstAttach = Files.createTempDirectory("litetask_zip_slip_att").toFile()
        val dstVoice = Files.createTempDirectory("litetask_zip_slip_voice").toFile()
        try {
            val zipBytes = ByteArrayOutputStream().also { out ->
                ZipOutputStream(out).use { zos ->
                    zos.putNextEntry(ZipEntry(BackupArchiveHelper.BACKUP_JSON_ENTRY))
                    zos.write("""{"tasks":[]}""".toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()

                    zos.putNextEntry(ZipEntry("attachments/../../escape.txt"))
                    zos.write("safe_only_inside".toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()
                }
            }.toByteArray()

            val res = BackupArchiveHelper.readArchiveAuto(
                ByteArrayInputStream(zipBytes),
                dstAttach,
                dstVoice
            )
            val target = res.extractedAttachments["escape.txt"]!!
            assertTrue(target.canonicalPath.startsWith(dstAttach.canonicalPath + File.separator))
        } finally {
            dstAttach.deleteRecursively()
            dstVoice.deleteRecursively()
        }
    }
}
