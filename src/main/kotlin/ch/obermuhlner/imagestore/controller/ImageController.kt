package ch.obermuhlner.imagestore.controller

import ch.obermuhlner.imagestore.config.StorageProperties
import ch.obermuhlner.imagestore.dto.ImageMetadataResponse
import ch.obermuhlner.imagestore.dto.ImageUploadResponse
import ch.obermuhlner.imagestore.dto.toMetadataResponse
import ch.obermuhlner.imagestore.dto.toUploadResponse
import ch.obermuhlner.imagestore.service.ImageService
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/images")
class ImageController(
    private val imageService: ImageService,
    private val storageProperties: StorageProperties
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun uploadImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("tags", required = false) tags: List<String>?
    ): ResponseEntity<ImageUploadResponse> {
        if (file.isEmpty) {
            return ResponseEntity.badRequest().build()
        }

        val contentType = file.contentType ?: "application/octet-stream"
        if (!contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().build()
        }

        val image = imageService.storeImage(
            data = file.bytes,
            filename = file.originalFilename ?: "unknown",
            contentType = contentType,
            tags = tags ?: emptyList()
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(image.toUploadResponse())
    }

    @GetMapping("/{id}")
    fun getImage(
        @PathVariable id: Long,
        @RequestHeader(value = "If-None-Match", required = false) ifNoneMatch: String?,
        @RequestHeader(value = "If-Modified-Since", required = false) ifModifiedSince: String?
    ): ResponseEntity<ByteArray> {
        val (image, data) = imageService.retrieveImage(id)

        // Check ETag first (stronger validation)
        val etag = generateETag(data)
        if (ifNoneMatch == etag) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CACHE_CONTROL, buildCacheControl().headerValue)
                .build()
        }

        // Check If-Modified-Since (weaker validation)
        if (ifModifiedSince != null && !isModifiedSince(image.uploadDate.toString(), ifModifiedSince)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CACHE_CONTROL, buildCacheControl().headerValue)
                .header(HttpHeaders.LAST_MODIFIED, image.uploadDate.toString())
                .build()
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(image.contentType))
            .header(HttpHeaders.ETAG, etag)
            .header(HttpHeaders.CACHE_CONTROL, buildCacheControl().headerValue)
            .header(HttpHeaders.LAST_MODIFIED, image.uploadDate.toString())
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"${image.filename}\"")
            .body(data)
    }

    private fun buildCacheControl(): CacheControl {
        return CacheControl.maxAge(storageProperties.cache.maxAge.toLong(), TimeUnit.SECONDS)
            .cachePublic()
    }

    private fun isModifiedSince(lastModified: String, ifModifiedSince: String): Boolean {
        return try {
            lastModified > ifModifiedSince
        } catch (e: Exception) {
            true
        }
    }

    @GetMapping("/{id}/metadata")
    fun getImageMetadata(@PathVariable id: Long): ResponseEntity<ImageMetadataResponse> {
        val image = imageService.getImageMetadata(id)
        return ResponseEntity.ok(image.toMetadataResponse())
    }

    @GetMapping("/search")
    fun searchImages(
        @RequestParam(required = false) required: List<String>?,
        @RequestParam(required = false) optional: List<String>?,
        @RequestParam(required = false) forbidden: List<String>?
    ): ResponseEntity<List<ImageMetadataResponse>> {
        val images = imageService.searchImages(
            requiredTags = required ?: emptyList(),
            optionalTags = optional ?: emptyList(),
            forbiddenTags = forbidden ?: emptyList()
        )
        return ResponseEntity.ok(images.map { it.toMetadataResponse() })
    }

    @DeleteMapping("/{id}")
    fun deleteImage(@PathVariable id: Long): ResponseEntity<Void> {
        imageService.deleteImage(id)
        return ResponseEntity.noContent().build()
    }

    private fun generateETag(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(data)
        return "\"${hash.joinToString("") { "%02x".format(it) }}\""
    }
}
