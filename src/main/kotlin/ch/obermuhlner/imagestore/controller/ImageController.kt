package ch.obermuhlner.imagestore.controller

import ch.obermuhlner.imagestore.config.StorageProperties
import ch.obermuhlner.imagestore.dto.ImageMetadataResponse
import ch.obermuhlner.imagestore.dto.ImageUploadResponse
import ch.obermuhlner.imagestore.dto.SignedUrlRequest
import ch.obermuhlner.imagestore.dto.SignedUrlResponse
import ch.obermuhlner.imagestore.dto.toMetadataResponse
import ch.obermuhlner.imagestore.dto.toUploadResponse
import ch.obermuhlner.imagestore.model.Permission
import ch.obermuhlner.imagestore.security.ApiKeyAuthentication
import ch.obermuhlner.imagestore.service.ImageAccessTokenService
import ch.obermuhlner.imagestore.service.ImageService
import ch.obermuhlner.imagestore.service.SignedUrlService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/images")
@Tag(name = "Images", description = "Image storage and retrieval operations")
class ImageController(
    private val imageService: ImageService,
    private val storageProperties: StorageProperties,
    private val signedUrlService: SignedUrlService?,
    private val tokenService: ImageAccessTokenService?,
    @Value("\${imagestore.security.enabled:false}")
    private val securityEnabled: Boolean
) {

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Upload an image", description = "Upload an image file with optional tags. Max size: 10MB. Supported formats: JPEG, PNG, GIF, WebP, SVG, BMP, TIFF. Requires UPLOAD permission when security is enabled.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Image uploaded successfully", content = [Content(schema = Schema(implementation = ImageUploadResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid file or file too large"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("!@environment.getProperty('imagestore.security.enabled', Boolean, false) || hasAuthority('UPLOAD')")
    fun uploadImage(
        @Parameter(description = "Image file to upload") @RequestParam("file") file: MultipartFile,
        @Parameter(description = "Optional tags for the image") @RequestParam("tags", required = false) tags: List<String>?
    ): ResponseEntity<ImageUploadResponse> {
        // Validate file is not empty
        if (file.isEmpty) {
            throw IllegalArgumentException("File cannot be empty")
        }

        // Validate content type
        val contentType = file.contentType ?: "application/octet-stream"
        if (!isValidImageType(contentType)) {
            throw IllegalArgumentException("Only image files are allowed. Received: $contentType")
        }

        // Validate file size (additional check beyond servlet multipart limits)
        val maxSize = 10 * 1024 * 1024L // 10MB
        if (file.size > maxSize) {
            throw IllegalArgumentException("File size exceeds maximum allowed size of ${maxSize / (1024 * 1024)}MB")
        }

        val image = imageService.storeImage(
            data = file.bytes,
            filename = file.originalFilename ?: "unknown",
            contentType = contentType,
            tags = tags ?: emptyList()
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(image.toUploadResponse())
    }

    private fun isValidImageType(contentType: String): Boolean {
        val validTypes = listOf(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "image/bmp",
            "image/tiff"
        )
        return validTypes.any { contentType.lowercase().startsWith(it) }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get image by ID", description = "Retrieve image data with CDN optimization headers (ETag, Cache-Control). Supports conditional GET with If-None-Match header. When security is enabled, requires either valid authentication OR a valid signed URL (signature + expires) OR a valid access token.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Image retrieved successfully", content = [Content(mediaType = "image/*")]),
        ApiResponse(responseCode = "304", description = "Not modified (cached version is still valid)"),
        ApiResponse(responseCode = "403", description = "Access denied - authentication, valid signed URL, or valid token required"),
        ApiResponse(responseCode = "404", description = "Image not found")
    ])
    fun getImage(
        @Parameter(description = "Image ID") @PathVariable id: Long,
        @Parameter(description = "HMAC signature for signed URL access") @RequestParam(required = false) signature: String?,
        @Parameter(description = "Expiry timestamp for signed URL access") @RequestParam(required = false) expires: Long?,
        @Parameter(description = "Permanent access token") @RequestParam(required = false) token: String?,
        @Parameter(description = "ETag for conditional GET") @RequestHeader(value = "If-None-Match", required = false) ifNoneMatch: String?,
        @Parameter(description = "Last modified date for conditional GET") @RequestHeader(value = "If-Modified-Since", required = false) ifModifiedSince: String?
    ): ResponseEntity<ByteArray> {
        // Validate access when security is enabled
        if (securityEnabled) {
            val authenticated = SecurityContextHolder.getContext().authentication?.isAuthenticated == true
            val hasValidSignedUrl = signature != null && expires != null &&
                signedUrlService?.validateSignedUrl(id, signature, expires) == true
            val hasValidToken = token != null && tokenService?.validateToken(id, token) == true

            if (!authenticated && !hasValidSignedUrl && !hasValidToken) {
                throw org.springframework.security.access.AccessDeniedException("Access denied")
            }
        }

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

    @PostMapping("/{id}/sign")
    @Operation(summary = "Generate signed URL", description = "Generate a time-limited signed URL for public access to an image. Requires GENERATE_SIGNED_URL permission.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Signed URL generated successfully", content = [Content(schema = Schema(implementation = SignedUrlResponse::class))]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        ApiResponse(responseCode = "404", description = "Image not found")
    ])
    @PreAuthorize("hasAuthority('GENERATE_SIGNED_URL')")
    fun generateSignedUrl(
        @Parameter(description = "Image ID") @PathVariable id: Long,
        @RequestBody(required = false) request: SignedUrlRequest?
    ): ResponseEntity<SignedUrlResponse> {
        // Verify image exists
        imageService.getImageMetadata(id)

        val response = signedUrlService!!.generateSignedUrl(id, request?.expiresIn)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{id}/metadata")
    @Operation(summary = "Get image metadata", description = "Retrieve image metadata including filename, content type, size, upload date, and tags. Requires SEARCH permission when security is enabled.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Metadata retrieved successfully", content = [Content(schema = Schema(implementation = ImageMetadataResponse::class))]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        ApiResponse(responseCode = "404", description = "Image not found")
    ])
    @PreAuthorize("!@environment.getProperty('imagestore.security.enabled', Boolean, false) || hasAuthority('SEARCH')")
    fun getImageMetadata(@Parameter(description = "Image ID") @PathVariable id: Long): ResponseEntity<ImageMetadataResponse> {
        val image = imageService.getImageMetadata(id)
        return ResponseEntity.ok(image.toMetadataResponse())
    }

    @GetMapping("/search")
    @Operation(summary = "Search images by tags", description = "Search images using tag-based query with AND/OR/NOT logic. Required tags (AND), optional tags (OR), and forbidden tags (NOT). Requires SEARCH permission when security is enabled.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Search completed successfully", content = [Content(schema = Schema(implementation = ImageMetadataResponse::class))]),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("!@environment.getProperty('imagestore.security.enabled', Boolean, false) || hasAuthority('SEARCH')")
    fun searchImages(
        @Parameter(description = "Tags that must all be present (AND logic)") @RequestParam(required = false) required: List<String>?,
        @Parameter(description = "Tags where at least one should be present (OR logic)") @RequestParam(required = false) optional: List<String>?,
        @Parameter(description = "Tags that must not be present (NOT logic)") @RequestParam(required = false) forbidden: List<String>?
    ): ResponseEntity<List<ImageMetadataResponse>> {
        val images = imageService.searchImages(
            requiredTags = required ?: emptyList(),
            optionalTags = optional ?: emptyList(),
            forbiddenTags = forbidden ?: emptyList()
        )
        return ResponseEntity.ok(images.map { it.toMetadataResponse() })
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete image", description = "Delete an image and its metadata from storage. Requires DELETE permission when security is enabled.")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Image deleted successfully"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        ApiResponse(responseCode = "404", description = "Image not found")
    ])
    @PreAuthorize("!@environment.getProperty('imagestore.security.enabled', Boolean, false) || hasAuthority('DELETE')")
    fun deleteImage(@Parameter(description = "Image ID") @PathVariable id: Long): ResponseEntity<Void> {
        imageService.deleteImage(id)
        return ResponseEntity.noContent().build()
    }

    private fun generateETag(data: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(data)
        return "\"${hash.joinToString("") { "%02x".format(it) }}\""
    }
}
