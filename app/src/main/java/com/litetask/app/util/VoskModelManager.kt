package com.litetask.app.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Vosk 模型管理器
 * 负责解压和管理语音识别模型
 */
object VoskModelManager {
    
    private const val MODEL_NAME = "vosk-model-small-cn-0.22"
    private const val MODEL_ZIP_NAME = "vosk-model-small-cn-0.22.zip"
    
    /**
     * 检查模型是否已存在
     */
    fun isModelExists(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_NAME)
        val markerFile = File(context.filesDir, ".vosk_model_extracted")
        
        // 检查模型目录和标记文件
        return modelDir.exists() && 
               modelDir.isDirectory && 
               modelDir.listFiles()?.isNotEmpty() == true &&
               markerFile.exists()
    }
    
    /**
     * 创建解压完成标记
     */
    private fun createExtractionMarker(context: Context) {
        val markerFile = File(context.filesDir, ".vosk_model_extracted")
        markerFile.createNewFile()
    }
    
    /**
     * 从 assets 解压模型到内部存储
     * 需要在后台线程调用
     * 
     * @param zipPath assets 中的 zip 文件路径，例如 "models/vosk-model-small-cn-0.22.zip"
     */
    suspend fun unpackModelFromAssets(context: Context, zipPath: String = MODEL_ZIP_NAME): Result<File> = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(context.filesDir, MODEL_NAME)
            
            // 如果已存在，直接返回
            if (isModelExists(context)) {
                return@withContext Result.success(modelDir)
            }
            
            // 创建目标目录
            if (!modelDir.exists()) {
                modelDir.mkdirs()
            }
            
            // 从 assets 读取 zip 文件
            context.assets.open(zipPath).use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry
                    
                    while (entry != null) {
                        val entryName = entry.name
                        
                        // 移除 zip 中的根目录名称（如果有）
                        val fileName = if (entryName.contains("/")) {
                            entryName.substring(entryName.indexOf("/") + 1)
                        } else {
                            entryName
                        }
                        
                        if (fileName.isEmpty()) {
                            entry = zipInputStream.nextEntry
                            continue
                        }
                        
                        val file = File(modelDir, fileName)
                        
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            // 确保父目录存在
                            file.parentFile?.mkdirs()
                            
                            // 解压文件
                            FileOutputStream(file).use { outputStream ->
                                zipInputStream.copyTo(outputStream)
                            }
                        }
                        
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
            }
            
            // 创建解压完成标记
            createExtractionMarker(context)
            
            Result.success(modelDir)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 从外部存储解压模型
     * 用于用户手动下载模型后解压
     * 
     * @param zipFile 外部存储中的 zip 文件
     */
    suspend fun unpackModelFromFile(context: Context, zipFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            val modelDir = File(context.filesDir, MODEL_NAME)
            
            // 如果已存在，先删除
            if (modelDir.exists()) {
                modelDir.deleteRecursively()
            }
            
            // 创建目标目录
            modelDir.mkdirs()
            
            // 解压 zip 文件
            FileInputStream(zipFile).use { fileInputStream ->
                ZipInputStream(fileInputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry
                    
                    while (entry != null) {
                        val entryName = entry.name
                        
                        // 移除 zip 中的根目录名称（如果有）
                        val fileName = if (entryName.contains("/")) {
                            entryName.substring(entryName.indexOf("/") + 1)
                        } else {
                            entryName
                        }
                        
                        if (fileName.isEmpty()) {
                            entry = zipInputStream.nextEntry
                            continue
                        }
                        
                        val file = File(modelDir, fileName)
                        
                        if (entry.isDirectory) {
                            file.mkdirs()
                        } else {
                            // 确保父目录存在
                            file.parentFile?.mkdirs()
                            
                            // 解压文件
                            FileOutputStream(file).use { outputStream ->
                                zipInputStream.copyTo(outputStream)
                            }
                        }
                        
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
            }
            
            // 创建解压完成标记
            createExtractionMarker(context)
            
            Result.success(modelDir)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * 获取模型目录
     */
    fun getModelDir(context: Context): File {
        return File(context.filesDir, MODEL_NAME)
    }
    
    /**
     * 删除模型
     */
    fun deleteModel(context: Context): Boolean {
        val modelDir = File(context.filesDir, MODEL_NAME)
        return if (modelDir.exists()) {
            modelDir.deleteRecursively()
        } else {
            true
        }
    }
    
    /**
     * 获取模型大小（MB）
     */
    fun getModelSize(context: Context): Double {
        val modelDir = File(context.filesDir, MODEL_NAME)
        if (!modelDir.exists()) return 0.0
        
        val sizeInBytes = modelDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        return sizeInBytes / (1024.0 * 1024.0)
    }
}
