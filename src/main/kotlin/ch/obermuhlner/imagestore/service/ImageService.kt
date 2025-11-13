package ch.obermuhlner.imagestore.service

import ch.obermuhlner.imagestore.config.StorageProperties
import ch.obermuhlner.imagestore.model.Image
import ch.obermuhlner.imagestore.model.Tag
import ch.obermuhlner.imagestore.repository.ImageRepository
import ch.obermuhlner.imagestore.repository.TagRepository
import ch.obermuhlner.imagestore.security.ApiKeyAuthentication
import ch.obermuhlner.imagestore.storage.StorageService
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class ImageService(
    private val imageRepository: ImageRepository,
    private val tagRepository: TagRepository,
    private val storageService: StorageService,
    private val storageProperties: StorageProperties
) {
    val logger = LoggerFactory.getLogger(javaClass)

    fun storeImage(data: ByteArray, filename: String, contentType: String, tags: List<String>): Image {
        val storagePath = storageService.store(data, filename)

        val tagEntities = tags.map { tagName ->
            val normalizedName = tagName.lowercase()
            tagRepository.findByName(normalizedName).orElseGet {
                tagRepository.save(Tag(name = normalizedName))
            }
        }.toMutableSet()

        // Get API key ID from authenticated user if available
        val uploadedByApiKeyId = (SecurityContextHolder.getContext().authentication as? ApiKeyAuthentication)
            ?.apiKey?.id

        val image = Image(
            filename = filename,
            contentType = contentType,
            size = data.size.toLong(),
            uploadDate = Instant.now(),
            storageType = storageProperties.storage.type,
            storagePath = storagePath,
            uploadedByApiKeyId = uploadedByApiKeyId,
            tags = tagEntities
        )

        return imageRepository.save(image)
    }

    fun retrieveImage(id: Long): Pair<Image, ByteArray> {
        val image = imageRepository.findById(id)
            .orElseThrow { NoSuchElementException("Image not found: $id") }
        val data = storageService.retrieve(image.storagePath)
        return Pair(image, data)
    }

    fun getImageMetadata(id: Long): Image {
        return imageRepository.findById(id)
            .orElseThrow { NoSuchElementException("Image not found: $id") }
    }

    fun searchImages(requiredTags: List<String>, optionalTags: List<String>, forbiddenTags: List<String>): List<Image> {
        val result = imageRepository.searchByTags(
            requiredTags = requiredTags.map { it.lowercase() },
            requiredTagsSize = requiredTags.size,
            optionalTags = optionalTags.map { it.lowercase() },
            optionalTagsSize = optionalTags.size,
            forbiddenTags = forbiddenTags.map { it.lowercase() },
            forbiddenTagsSize = forbiddenTags.size
        )
        logger.info("Found ${result.size} images for required=$requiredTags optional=$optionalTags forbidden=$forbiddenTags")
        return result
    }

    fun deleteImage(id: Long) {
        val image = imageRepository.findById(id)
            .orElseThrow { NoSuchElementException("Image not found: $id") }
        storageService.delete(image.storagePath)
        imageRepository.delete(image)
    }
}
