package com.litetask.app.util

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * 使用 Android 原生 MediaExtractor + MediaCodec 将 `.m4a` 等压缩音频解码并重采样为 16kHz 16-bit 单声道 PCM。
 */
object AudioPcmDecoder {

    private const val TAG = "AudioPcmDecoder"
    private const val TARGET_SAMPLE_RATE = 16_000
    private const val DECODE_TIMEOUT_US = 10_000L
    private const val MAX_OUTPUT_PCM_BYTES = 12 * 1024 * 1024 // ~6 分钟 16kHz mono 16-bit

    fun decodeTo16kMonoPcm(file: File): ByteArray? {
        if (!file.exists() || !file.isFile || !file.canRead()) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = fmt
                    break
                }
            }
            if (audioTrackIndex < 0 || inputFormat == null) return null

            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            var sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else TARGET_SAMPLE_RATE
            var channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 1

            extractor.selectTrack(audioTrackIndex)
            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            val rawPcmOut = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && rawPcmOut.size() < MAX_OUTPUT_PCM_BYTES * 2) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(DECODE_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)
                        if (inBuf != null) {
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inIdx, 0, sampleSize, extractor.sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, DECODE_TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.get(chunk)
                            val monoChunk = downmixAndConvertTo16BitLittleEndian(chunk, channelCount)
                            rawPcmOut.write(monoChunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                        }
                    }
                }
            }

            val rawMonoPcm = rawPcmOut.toByteArray()
            if (rawMonoPcm.isEmpty()) return null
            val resampled = if (sampleRate == TARGET_SAMPLE_RATE) {
                rawMonoPcm
            } else {
                resampleLinear16BitMono(rawMonoPcm, sampleRate, TARGET_SAMPLE_RATE)
            }
            return if (resampled.size > MAX_OUTPUT_PCM_BYTES) {
                resampled.copyOf(MAX_OUTPUT_PCM_BYTES and -2)
            } else {
                resampled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Decode m4a to PCM failed", e)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun downmixAndConvertTo16BitLittleEndian(pcmBytes: ByteArray, channelCount: Int): ByteArray {
        if (channelCount <= 1 && ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
            return pcmBytes
        }
        val safeChannels = channelCount.coerceAtLeast(1)
        val totalSamples = pcmBytes.size / (2 * safeChannels)
        if (totalSamples <= 0) return ByteArray(0)
        val out = ByteArray(totalSamples * 2)
        val isNativeBe = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN
        for (frame in 0 until totalSamples) {
            var sum = 0
            for (ch in 0 until safeChannels) {
                val idx = (frame * safeChannels + ch) * 2
                val lo = pcmBytes[idx].toInt() and 0xFF
                val hi = pcmBytes[idx + 1].toInt() and 0xFF
                val sample = if (isNativeBe) ((lo shl 8) or hi).toShort() else ((hi shl 8) or lo).toShort()
                sum += sample.toInt()
            }
            val mono = (sum / safeChannels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            out[frame * 2] = (mono.toInt() and 0xFF).toByte()
            out[frame * 2 + 1] = ((mono.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun resampleLinear16BitMono(pcm: ByteArray, srcRate: Int, dstRate: Int): ByteArray {
        val srcSamples = pcm.size / 2
        if (srcSamples <= 1 || srcRate <= 0 || srcRate == dstRate) return pcm
        val dstSamples = ((srcSamples.toDouble() * dstRate) / srcRate).roundToInt().coerceAtLeast(1)
        val out = ByteArray(dstSamples * 2)

        fun readSample(index: Int): Int {
            val i = index.coerceIn(0, srcSamples - 1) * 2
            val lo = pcm[i].toInt() and 0xFF
            val hi = pcm[i + 1].toInt() and 0xFF
            return ((hi shl 8) or lo).toShort().toInt()
        }

        for (i in 0 until dstSamples) {
            val srcPos = i.toDouble() * (srcSamples - 1) / (dstSamples - 1).coerceAtLeast(1)
            val loIdx = srcPos.toInt().coerceIn(0, srcSamples - 1)
            val hiIdx = (loIdx + 1).coerceAtMost(srcSamples - 1)
            val frac = srcPos - loIdx
            val interpolated = (readSample(loIdx) * (1.0 - frac) + readSample(hiIdx) * frac)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[i * 2] = (interpolated and 0xFF).toByte()
            out[i * 2 + 1] = ((interpolated shr 8) and 0xFF).toByte()
        }
        return out
    }
}
