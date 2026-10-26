package com.litetask.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.litetask.app.data.model.FileAttachmentData
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 附件内容多模态解析与内嵌预览渲染助手。
 *
 * 同时服务于：
 * 1. 应用内预览查看器（图片缩放、PDF 逐页渲染、文本有界预览）；
 * 2. AI 任务拆解上下文提取（读取纯文本文档摘要，或将图片/PDF 关键页转为 Base64 供多模态模型理解）。
 */
object AttachmentContentReader {

    enum class AttachmentPreviewType {
        IMAGE,
        PDF,
        TEXT,
        UNSUPPORTED
    }

    data class TextPreviewResult(
        val content: String,
        val isTruncated: Boolean
    )

    private val TEXT_EXTENSIONS = setOf(
        "txt", "md", "markdown", "csv", "tsv", "json", "log",
        "xml", "yaml", "yml", "ini", "conf", "properties",
        "kt", "java", "py", "js", "ts", "html", "css", "sql"
    )

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif"
    )

    fun detectPreviewType(fileName: String, mimeType: String): AttachmentPreviewType {
        val mime = mimeType.lowercase(Locale.ROOT)
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when {
            mime.startsWith("image/") || ext in IMAGE_EXTENSIONS -> AttachmentPreviewType.IMAGE
            mime == "application/pdf" || ext == "pdf" -> AttachmentPreviewType.PDF
            mime.startsWith("text/") ||
                mime == "application/json" ||
                mime == "application/xml" ||
                ext in TEXT_EXTENSIONS -> AttachmentPreviewType.TEXT
            else -> AttachmentPreviewType.UNSUPPORTED
        }
    }

    fun detectPreviewType(data: FileAttachmentData): AttachmentPreviewType =
        detectPreviewType(data.fileName, data.mimeType)

    fun readTextPreview(
        context: Context,
        data: FileAttachmentData,
        maxChars: Int = 6_000,
        maxBytes: Long = 64L * 1024L
    ): TextPreviewResult? {
        if (detectPreviewType(data) != AttachmentPreviewType.TEXT) return null
        return runCatching {
            AttachmentStorageHelper.openInputStream(context, data)?.use { input ->
                val raw = readBytesUpTo(input, maxBytes + 1)
                val byteTruncated = raw.size > maxBytes
                val slice = if (byteTruncated) raw.copyOf(maxBytes.toInt()) else raw
                var text = decodeUtf8Text(slice)
                val charTruncated = text.length > maxChars
                if (charTruncated) {
                    text = text.take(maxChars)
                }
                TextPreviewResult(
                    content = text,
                    isTruncated = byteTruncated || charTruncated
                )
            }
        }.getOrNull()
    }

    fun decodeUtf8Text(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val hasUtf8Bom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        val start = if (hasUtf8Bom) 3 else 0
        return String(bytes, start, bytes.size - start, StandardCharsets.UTF_8)
    }

    private fun readBytesUpTo(input: java.io.InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var remaining = limit
        while (remaining > 0L) {
            val toRead = minOf(buf.size.toLong(), remaining).toInt()
            val n = input.read(buf, 0, toRead)
            if (n <= 0) break
            out.write(buf, 0, n)
            remaining -= n
        }
        return out.toByteArray()
    }

    fun decodeBitmapForPreview(
        context: Context,
        data: FileAttachmentData,
        maxDimensionPx: Int = 2048
    ): Bitmap? {
        if (detectPreviewType(data) != AttachmentPreviewType.IMAGE) return null
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            AttachmentStorageHelper.openInputStream(context, data)?.use {
                BitmapFactory.decodeStream(it, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

            val sampleSize = calculateInSampleSize(
                boundsOpts.outWidth,
                boundsOpts.outHeight,
                maxDimensionPx
            )
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = AttachmentStorageHelper.openInputStream(context, data)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            val orientation = AttachmentStorageHelper.openInputStream(context, data)?.use {
                runCatching {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }.getOrNull()
            } ?: ExifInterface.ORIENTATION_NORMAL

            applyExifOrientation(bitmap, orientation)
        }.getOrNull()
    }

    fun getPdfPageCount(context: Context, data: FileAttachmentData): Int {
        if (detectPreviewType(data) != AttachmentPreviewType.PDF) return 0
        return runCatching {
            openSeekableParcelFd(context, data)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            } ?: 0
        }.getOrDefault(0)
    }

    fun renderPdfPage(
        context: Context,
        data: FileAttachmentData,
        pageIndex: Int,
        targetWidthPx: Int = 1440
    ): Bitmap? {
        if (detectPreviewType(data) != AttachmentPreviewType.PDF) return null
        return runCatching {
            openSeekableParcelFd(context, data)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (pageIndex !in 0 until renderer.pageCount) return null
                    renderer.openPage(pageIndex).use { page ->
                        val aspect = if (page.width > 0) page.height.toFloat() / page.width else 1.414f
                        val width = targetWidthPx.coerceIn(480, 2048)
                        val height = (width * aspect).roundToInt().coerceIn(480, 3072)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        // PDF 页默认背景透明，统一铺白底避免转 JPEG 或深色模式下变黑
                        Canvas(bitmap).drawColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }.getOrNull()
    }

    /**
     * 提取单个任务下适合传入 AI 上下文的文本摘要与多模态图片（图片附件 + PDF 前几页）。
     */
    fun extractAiContextFromAttachments(
        context: Context,
        attachments: List<FileAttachmentData>,
        includeVisualMedia: Boolean,
        maxTextFiles: Int = 4,
        maxImages: Int = 3
    ): Pair<List<Pair<String, String>>, List<String>> {
        val textExcerpts = mutableListOf<Pair<String, String>>()
        val encodedImages = mutableListOf<String>()

        for (item in attachments) {
            when (detectPreviewType(item)) {
                AttachmentPreviewType.TEXT -> {
                    if (textExcerpts.size < maxTextFiles) {
                        readTextPreview(context, item, maxChars = 2_400, maxBytes = 24L * 1024L)?.let { preview ->
                            val clean = preview.content.trim()
                            if (clean.isNotEmpty()) {
                                val label = item.effectiveDisplayName()
                                val suffix = if (preview.isTruncated) "\n...（文件较长，已截断）" else ""
                                textExcerpts.add(label to (clean + suffix))
                            }
                        }
                    }
                }
                AttachmentPreviewType.IMAGE -> {
                    if (includeVisualMedia && encodedImages.size < maxImages) {
                        decodeBitmapForPreview(context, item, maxDimensionPx = 1280)?.let { bmp ->
                            bitmapToBase64Jpeg(bmp, quality = 80)?.let { b64 ->
                                encodedImages.add(b64)
                            }
                            bmp.recycle()
                        }
                    }
                }
                AttachmentPreviewType.PDF -> {
                    if (includeVisualMedia && encodedImages.size < maxImages) {
                        val pageCount = getPdfPageCount(context, item).coerceAtMost(maxImages - encodedImages.size)
                        for (p in 0 until pageCount) {
                            renderPdfPage(context, item, pageIndex = p, targetWidthPx = 1280)?.let { bmp ->
                                bitmapToBase64Jpeg(bmp, quality = 80)?.let { b64 ->
                                    encodedImages.add(b64)
                                }
                                bmp.recycle()
                            }
                        }
                    }
                }
                AttachmentPreviewType.UNSUPPORTED -> Unit
            }
        }
        return textExcerpts to encodedImages
    }

    private fun openSeekableParcelFd(context: Context, data: FileAttachmentData): ParcelFileDescriptor? {
        data.managedFilePath?.let { path ->
            val file = File(path)
            if (file.exists() && file.canRead()) {
                return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        val uri = Uri.parse(data.fileUri)
        return context.contentResolver.openFileDescriptor(uri, "r")
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        while ((largest / inSampleSize) > maxDim) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        }.getOrElse { bitmap }
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap, quality: Int): String? {
        return runCatching {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val bytes = stream.toByteArray()
            if (bytes.size > 1_600_000) {
                stream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            }
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }
}
