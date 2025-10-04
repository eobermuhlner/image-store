package ch.obermuhlner.imagestore.repository

import ch.obermuhlner.imagestore.model.Image
import ch.obermuhlner.imagestore.model.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DataJpaTest
class ImageRepositoryTest {

    @Autowired
    private lateinit var imageRepository: ImageRepository

    @Autowired
    private lateinit var tagRepository: TagRepository

    @Test
    fun `should save and retrieve image`() {
        val image = Image(
            filename = "test.jpg",
            contentType = "image/jpeg",
            size = 1024,
            storageType = "filesystem",
            storagePath = "/path/to/image"
        )

        val saved = imageRepository.save(image)
        assertNotNull(saved.id)

        val retrieved = imageRepository.findById(saved.id!!).orElse(null)
        assertNotNull(retrieved)
        assertEquals("test.jpg", retrieved.filename)
    }

    @Test
    fun `should save image with tags`() {
        val tag1 = tagRepository.save(Tag(name = "nature"))
        val tag2 = tagRepository.save(Tag(name = "landscape"))

        val image = Image(
            filename = "mountain.jpg",
            contentType = "image/jpeg",
            size = 2048,
            storageType = "filesystem",
            storagePath = "/path/to/mountain",
            tags = mutableSetOf(tag1, tag2)
        )

        val saved = imageRepository.save(image)
        val retrieved = imageRepository.findById(saved.id!!).orElse(null)

        assertNotNull(retrieved)
        assertEquals(2, retrieved.tags.size)
        assertTrue(retrieved.tags.any { it.name == "nature" })
        assertTrue(retrieved.tags.any { it.name == "landscape" })
    }

    @Test
    fun `should search images by required tags`() {
        val tag1 = tagRepository.save(Tag(name = "cat"))
        val tag2 = tagRepository.save(Tag(name = "pet"))
        val tag3 = tagRepository.save(Tag(name = "wild"))

        val image1 = imageRepository.save(
            Image(
                filename = "cat1.jpg",
                contentType = "image/jpeg",
                size = 1024,
                storageType = "filesystem",
                storagePath = "/cat1",
                tags = mutableSetOf(tag1, tag2)
            )
        )

        val image2 = imageRepository.save(
            Image(
                filename = "cat2.jpg",
                contentType = "image/jpeg",
                size = 1024,
                storageType = "filesystem",
                storagePath = "/cat2",
                tags = mutableSetOf(tag1, tag3)
            )
        )

        val results = imageRepository.searchByTags(
            requiredTags = listOf("cat", "pet"),
            requiredTagsSize = 2,
            optionalTags = emptyList(),
            optionalTagsSize = 0,
            forbiddenTags = emptyList(),
            forbiddenTagsSize = 0
        )

        assertEquals(1, results.size)
        assertEquals("cat1.jpg", results[0].filename)
    }

    @Test
    fun `should search images excluding forbidden tags`() {
        val tag1 = tagRepository.save(Tag(name = "dog"))
        val tag2 = tagRepository.save(Tag(name = "indoor"))
        val tag3 = tagRepository.save(Tag(name = "outdoor"))

        imageRepository.save(
            Image(
                filename = "dog1.jpg",
                contentType = "image/jpeg",
                size = 1024,
                storageType = "filesystem",
                storagePath = "/dog1",
                tags = mutableSetOf(tag1, tag2)
            )
        )

        imageRepository.save(
            Image(
                filename = "dog2.jpg",
                contentType = "image/jpeg",
                size = 1024,
                storageType = "filesystem",
                storagePath = "/dog2",
                tags = mutableSetOf(tag1, tag3)
            )
        )

        val results = imageRepository.searchByTags(
            requiredTags = listOf("dog"),
            requiredTagsSize = 1,
            optionalTags = emptyList(),
            optionalTagsSize = 0,
            forbiddenTags = listOf("indoor"),
            forbiddenTagsSize = 1
        )

        assertEquals(1, results.size)
        assertEquals("dog2.jpg", results[0].filename)
    }
}
