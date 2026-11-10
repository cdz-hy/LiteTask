package com.litetask.app.data.model

import com.google.gson.Gson
import com.litetask.app.util.BackupArchiveHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class VoiceNoteSupportTest {

    private val gson = Gson()

    @Test
    fun normalizeWaveformPeaks_handlesEmptyShortExactAndLongInputs() {
        val empty = VoiceNoteSupport.normalizeWaveformPeaks(emptyList())
        assertEquals(VoiceNoteSupport.WAVEFORM_BAR_COUNT, empty.size)
        assertTrue(empty.all { it == 12 })

        val short = VoiceNoteSupport.normalizeWaveformPeaks(listOf(0, 16000, 32000))
        assertEquals(VoiceNoteSupport.WAVEFORM_BAR_COUNT, short.size)
        assertTrue(short.all { it in 8..100 })
        assertTrue(short.last() >= short.first())

        val long = VoiceNoteSupport.normalizeWaveformPeaks(List(150) { idx -> if (idx == 75) 30000 else 1000 })
        assertEquals(VoiceNoteSupport.WAVEFORM_BAR_COUNT, long.size)
        assertTrue(long.all { it in 8..100 })
        assertTrue(long[VoiceNoteSupport.WAVEFORM_BAR_COUNT / 2] >= 90)
    }

    @Test
    fun durationClockAndPlaybackSpeed_formatAndCycleProperly() {
        assertEquals("00:00", VoiceNoteSupport.formatDurationClock(0L))
        assertEquals("01:05", VoiceNoteSupport.formatDurationClock(65_400L))
        assertEquals("03:00", VoiceNoteSupport.formatDurationClock(180_000L))

        assertEquals("0.75x", VoiceNoteSupport.formatSpeedLabel(0.75f))
        assertEquals("1.0x", VoiceNoteSupport.formatSpeedLabel(1.0f))
        assertEquals("1.25x", VoiceNoteSupport.formatSpeedLabel(1.25f))
        assertEquals("1.5x", VoiceNoteSupport.formatSpeedLabel(1.5f))

        assertEquals(1.25f, VoiceNoteSupport.nextPlaybackSpeed(1.0f), 0.001f)
        assertEquals(0.75f, VoiceNoteSupport.nextPlaybackSpeed(1.5f), 0.001f)
    }

    @Test
    fun voiceNoteData_gsonFullAndShortKeys_andEntityRoundTrip_work() {
        val shortJson = """
            {
              "a": "/data/user/0/com.litetask.app/files/voice_notes/vn_1.m4a",
              "b": "content://com.litetask.app.fileprovider/voice_notes/vn_1.m4a",
              "c": 42000,
              "d": [12, 48, 85, 30],
              "e": "下午三点前提交季度复盘文档",
              "f": "复盘会录音"
            }
        """.trimIndent()
        val parsed = gson.fromJson(shortJson, VoiceNoteData::class.java)
        assertEquals("/data/user/0/com.litetask.app/files/voice_notes/vn_1.m4a", parsed.filePath)
        assertEquals(42000L, parsed.durationMs)
        assertEquals(listOf(12, 48, 85, 30), parsed.waveformPeaks)
        assertEquals("下午三点前提交季度复盘文档", parsed.transcript)
        assertEquals("复盘会录音", parsed.label)

        val entity = TaskComponent.VoiceNoteComponent(id = 7L, taskId = 15L, data = parsed).toEntity(15L)
        assertEquals(ComponentType.VOICE_NOTE, entity.type)
        val roundTripped = entity.toTaskComponent() as TaskComponent.VoiceNoteComponent
        assertEquals(parsed.filePath, roundTripped.data.filePath)
        assertEquals(parsed.transcript, roundTripped.data.transcript)
    }

    @Test
    fun componentRegistryAndSearchAndRecurrenceInheritance_handleVoiceNote() {
        val vn = TaskComponent.VoiceNoteComponent(
            id = 3L,
            taskId = 20L,
            data = VoiceNoteData(
                filePath = "/tmp/vn_test.m4a",
                durationMs = 74_000L,
                waveformPeaks = listOf(20, 60, 40),
                transcript = "记得联系张工确认接口文档",
                label = "需求沟通"
            )
        )

        assertEquals("语音备忘", TaskComponentRegistry.displayTypeName(ComponentType.VOICE_NOTE))
        assertEquals("需求沟通 · 01:14", TaskComponentRegistry.chipSummary(vn))
        assertTrue(TaskComponentRegistry.searchableText(vn).contains("接口文档"))

        val calLines = TaskComponentRegistry.formatCalendarDescriptionLines(listOf(vn))
        assertEquals(1, calLines.size)
        assertTrue(calLines.first().contains("01:14"))
        assertTrue(calLines.first().contains("需求沟通"))

        val aiJson = TaskComponentRegistry.toAiJsonObject(vn)
        assertNotNull(aiJson)
        assertEquals("VOICE_NOTE", aiJson!!.get("type").asString)
        assertEquals("01:14", aiJson.get("duration_clock").asString)

        val composite = TaskDetailComposite(
            task = Task(id = 20L, title = "上线准备", type = TaskType.WORK, startTime = 1_000L, deadline = 2_000L),
            subTasks = emptyList(),
            reminders = emptyList(),
            components = listOf(vn.toEntity(20L)),
            category = null
        )
        assertTrue(TaskComponentRegistry.matchesSearchQuery(composite, "接口文档"))
        assertFalse(TaskComponentRegistry.matchesSearchQuery(composite, "无关关键字"))

        // Recurrence must skip VoiceNoteComponent on next occurrence spawn
        val rule = RecurrenceRuleData(freq = "WEEKLY", daysOfWeek = listOf(1))
        val updatedRule = RecurrenceEngine.advanceRuleForNextOccurrence(rule)
        val inherited = RecurrenceEngine.buildInheritedComponents(
            sourceComponents = listOf(vn, TaskComponent.RecurrenceComponent(id = 4L, taskId = 20L, data = rule)),
            updatedRule = updatedRule
        )
        assertTrue(inherited.none { it is TaskComponent.VoiceNoteComponent })
        assertTrue(inherited.any { it is TaskComponent.RecurrenceComponent })
    }

    @Test
    fun backupArchive_zipExportImport_remapsVoiceNotePathAndUri() {
        val tempRoot = Files.createTempDirectory("litetask_vn_test").toFile()
        try {
            val srcVoice = File(tempRoot, "vn_demo_123.m4a").apply {
                writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04))
            }
            val originalComp = TaskComponent.VoiceNoteComponent(
                id = 1L,
                taskId = 2L,
                data = VoiceNoteData(
                    filePath = "/old/device/files/voice_notes/vn_demo_123.m4a",
                    fileUri = "content://old.provider/vn_demo_123.m4a",
                    durationMs = 15_000L,
                    transcript = "备份音频内容"
                )
            ).toEntity(2L)

            val zipBytes = ByteArrayOutputStream().also { out ->
                BackupArchiveHelper.writeZipArchive(
                    outputStream = out,
                    backupJson = "{\"ok\":true}",
                    attachmentFiles = emptyList(),
                    voiceNoteFiles = listOf(srcVoice)
                )
            }.toByteArray()

            val targetAttDir = File(tempRoot, "restored_att")
            val targetVnDir = File(tempRoot, "restored_vn")
            val readResult = BackupArchiveHelper.readArchiveAuto(
                inputStream = ByteArrayInputStream(zipBytes),
                targetAttachmentsDir = targetAttDir,
                targetVoiceNotesDir = targetVnDir
            )
            assertTrue(readResult.isZipArchive)
            assertTrue(readResult.extractedVoiceNotes.containsKey("vn_demo_123.m4a"))

            val remapped = BackupArchiveHelper.remapRestoredComponent(
                entity = originalComp,
                extractedAttachments = readResult.extractedAttachments,
                extractedVoiceNotes = readResult.extractedVoiceNotes,
                fileUriResolver = { f -> "content://new.provider/voice_notes/${f.name}" }
            )
            val restoredVn = remapped.toTaskComponent() as TaskComponent.VoiceNoteComponent
            assertEquals(
                File(targetVnDir, "vn_demo_123.m4a").canonicalPath,
                File(restoredVn.data.filePath).canonicalPath
            )
            assertEquals("content://new.provider/voice_notes/vn_demo_123.m4a", restoredVn.data.fileUri)
            assertEquals("备份音频内容", restoredVn.data.transcript)
        } finally {
            tempRoot.deleteRecursively()
        }
    }
}
