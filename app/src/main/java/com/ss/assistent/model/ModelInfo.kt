package com.ss.assistent.model

import java.util.UUID

/** A model known to SS Assistent and stored outside the APK. */
data class ModelInfo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val path: String,
    val isActive: Boolean = false
) {
    val sizeLabel: String
        get() {
            val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (gb >= 1.0) "%.2f GB".format(gb) else "%.0f MB".format(mb)
        }
}
