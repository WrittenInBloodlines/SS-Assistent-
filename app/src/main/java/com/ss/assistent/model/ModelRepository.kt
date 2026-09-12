package com.ss.assistent.model

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

class ModelRepository(private val context: Context) {
    private val modelsDir = File(context.filesDir, "models")
    private val metadataFile = File(modelsDir, "models.properties")

    init {
        modelsDir.mkdirs()
    }

    fun getModels(): List<ModelInfo> {
        if (!metadataFile.exists()) return emptyList()
        val properties = Properties()
        metadataFile.inputStream().use { properties.load(it) }
        return properties.stringPropertyNames()
            .filter { it.endsWith(".name") }
            .mapNotNull { key ->
                val id = key.removeSuffix(".name")
                val name = properties.getProperty(key) ?: return@mapNotNull null
                val fileName = properties.getProperty("$id.fileName") ?: return@mapNotNull null
                val size = properties.getProperty("$id.size")?.toLongOrNull() ?: return@mapNotNull null
                val path = properties.getProperty("$id.path") ?: return@mapNotNull null
                ModelInfo(
                    id = id,
                    name = name,
                    fileName = fileName,
                    sizeBytes = size,
                    path = path,
                    isActive = properties.getProperty("$id.active") == "true"
                )
            }
            .filter { File(it.path).exists() }
            .sortedBy { it.name.lowercase() }
    }

    fun importModel(uri: Uri): ModelInfo {
        val resolver = context.contentResolver
        val originalName = queryDisplayName(uri) ?: "model.gguf"
        require(originalName.lowercase().endsWith(".gguf")) {
            "Only .gguf model files are supported."
        }

        val safeName = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = uniqueTarget(File(modelsDir, safeName))
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, DEFAULT_BUFFER_SIZE)
            }
        } ?: error("Could not read the selected model file.")

        validateGguf(target)
        val id = java.util.UUID.randomUUID().toString()
        val model = ModelInfo(
            id = id,
            name = target.nameWithoutExtension,
            fileName = target.name,
            sizeBytes = target.length(),
            path = target.absolutePath,
            isActive = getModels().isEmpty()
        )
        saveModel(model)
        return model
    }

    fun setActive(id: String) {
        val updated = getModels().map { it.copy(isActive = it.id == id) }
        saveAll(updated)
    }

    fun delete(id: String) {
        val model = getModels().firstOrNull { it.id == id } ?: return
        File(model.path).delete()
        saveAll(getModels().filterNot { it.id == id }.let { models ->
            if (models.none { it.isActive } && models.isNotEmpty()) {
                models.mapIndexed { index, item -> item.copy(isActive = index == 0) }
            } else models
        })
    }

    private fun saveModel(model: ModelInfo) {
        saveAll(getModels().filterNot { it.id == model.id } + model)
    }

    private fun saveAll(models: List<ModelInfo>) {
        val properties = Properties()
        models.forEach { model ->
            properties.setProperty("${model.id}.name", model.name)
            properties.setProperty("${model.id}.fileName", model.fileName)
            properties.setProperty("${model.id}.size", model.sizeBytes.toString())
            properties.setProperty("${model.id}.path", model.path)
            properties.setProperty("${model.id}.active", model.isActive.toString())
        }
        FileOutputStream(metadataFile).use { properties.store(it, "SS Assistent models") }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun uniqueTarget(original: File): File {
        if (!original.exists()) return original
        var index = 2
        while (true) {
            val candidate = File(original.parentFile, "${original.nameWithoutExtension}-$index.${original.extension}")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun validateGguf(file: File) {
        FileInputStream(file).use { input ->
            val header = ByteArray(4)
            if (input.read(header) != 4 || String(header, Charsets.US_ASCII) != "GGUF") {
                file.delete()
                throw IllegalArgumentException("The selected file is not a valid GGUF model.")
            }
        }
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 1024 * 1024
    }
}
