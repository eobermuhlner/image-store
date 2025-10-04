package ch.obermuhlner.imagestore.dto

import ch.obermuhlner.imagestore.model.Image
import java.time.Instant

data class ImageUploadResponse(
    val id: Long,
    val filename: String,
    val contentType: String,
    val size: Long,
    val uploadDate: Instant,
    val tags: List<String>
)

data class ImageMetadataResponse(
    val id: Long,
    val filename: String,
    val contentType: String,
    val size: Long,
    val uploadDate: Instant,
    val storageType: String,
    val tags: List<String>
)

fun Image.toUploadResponse() = ImageUploadResponse(
    id = id!!,
    filename = filename,
    contentType = contentType,
    size = size,
    uploadDate = uploadDate,
    tags = tags.map { it.name }
)

fun Image.toMetadataResponse() = ImageMetadataResponse(
    id = id!!,
    filename = filename,
    contentType = contentType,
    size = size,
    uploadDate = uploadDate,
    storageType = storageType,
    tags = tags.map { it.name }
)
