package ch.obermuhlner.imagestore.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class TagSearchIntegrationTest {

    @Autowired
    private lateinit var imageService: ImageService

    @BeforeEach
    fun setup() {
        // Create test images with various tag combinations
        // Image 1: cat, pet, indoor
        imageService.storeImage(
            data = "image1".toByteArray(),
            filename = "cat1.jpg",
            contentType = "image/jpeg",
            tags = listOf("cat", "pet", "indoor")
        )

        // Image 2: cat, pet, outdoor
        imageService.storeImage(
            data = "image2".toByteArray(),
            filename = "cat2.jpg",
            contentType = "image/jpeg",
            tags = listOf("cat", "pet", "outdoor")
        )

        // Image 3: cat, wild, outdoor
        imageService.storeImage(
            data = "image3".toByteArray(),
            filename = "wildcat.jpg",
            contentType = "image/jpeg",
            tags = listOf("cat", "wild", "outdoor")
        )

        // Image 4: dog, pet, indoor
        imageService.storeImage(
            data = "image4".toByteArray(),
            filename = "dog1.jpg",
            contentType = "image/jpeg",
            tags = listOf("dog", "pet", "indoor")
        )

        // Image 5: bird, pet, indoor
        imageService.storeImage(
            data = "image5".toByteArray(),
            filename = "bird.jpg",
            contentType = "image/jpeg",
            tags = listOf("bird", "pet", "indoor")
        )
    }

    @Test
    fun `should find images with single required tag`() {
        val results = imageService.searchImages(
            requiredTags = listOf("dog"),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        assertEquals(1, results.size)
        assertTrue(results.any { it.filename == "dog1.jpg" })
    }

    @Test
    fun `should find images with multiple required tags (AND)`() {
        val results = imageService.searchImages(
            requiredTags = listOf("cat", "pet"),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        assertEquals(2, results.size)
        assertTrue(results.any { it.filename == "cat1.jpg" })
        assertTrue(results.any { it.filename == "cat2.jpg" })
    }

    @Test
    fun `should find images with all three required tags`() {
        val results = imageService.searchImages(
            requiredTags = listOf("cat", "pet", "indoor"),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        assertEquals(1, results.size)
        assertEquals("cat1.jpg", results[0].filename)
    }

    @Test
    fun `should find images excluding forbidden tags (NOT)`() {
        val results = imageService.searchImages(
            requiredTags = listOf("cat"),
            optionalTags = emptyList(),
            forbiddenTags = listOf("wild")
        )

        assertEquals(2, results.size)
        assertTrue(results.none { it.filename == "wildcat.jpg" })
        assertTrue(results.any { it.filename == "cat1.jpg" })
        assertTrue(results.any { it.filename == "cat2.jpg" })
    }

    @Test
    fun `should find images with optional tags (OR)`() {
        val results = imageService.searchImages(
            requiredTags = emptyList(),
            optionalTags = listOf("indoor"),
            forbiddenTags = emptyList()
        )

        // With new behavior (optional tags for ranking only), all images should be returned
        // ranked by how many optional tags they match (indoor tag matches: cat1, dog1, bird)
        assertEquals(5, results.size)
        // First 3 results should be images with "indoor" tag, followed by others
        assertEquals("cat1.jpg", results[0].filename) // cat, pet, indoor
        assertEquals("dog1.jpg", results[1].filename) // dog, pet, indoor
        assertEquals("bird.jpg", results[2].filename) // bird, pet, indoor
        // The remaining 2 should be without "indoor" tag
        assertTrue(listOf("cat2.jpg", "wildcat.jpg").contains(results[3].filename))
        assertTrue(listOf("cat2.jpg", "wildcat.jpg").contains(results[4].filename))
    }

    @Test
    fun `should combine required and forbidden tags`() {
        val results = imageService.searchImages(
            requiredTags = listOf("pet"),
            optionalTags = emptyList(),
            forbiddenTags = listOf("dog", "bird")
        )

        assertEquals(2, results.size)
        assertTrue(results.any { it.filename == "cat1.jpg" })
        assertTrue(results.any { it.filename == "cat2.jpg" })
    }

    @Test
    fun `should combine required, optional, and forbidden tags`() {
        val results = imageService.searchImages(
            requiredTags = listOf("cat"),
            optionalTags = listOf("indoor", "outdoor"),
            forbiddenTags = listOf("wild")
        )

        assertEquals(2, results.size)
        assertTrue(results.any { it.filename == "cat1.jpg" })
        assertTrue(results.any { it.filename == "cat2.jpg" })
        assertTrue(results.none { it.filename == "wildcat.jpg" })
    }

    @Test
    fun `should return all images when no filters specified`() {
        val results = imageService.searchImages(
            requiredTags = emptyList(),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        assertEquals(5, results.size)
    }

    @Test
    fun `should return empty list when required tags don't match any image`() {
        val results = imageService.searchImages(
            requiredTags = listOf("elephant"),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `should exclude all images with forbidden tag`() {
        val results = imageService.searchImages(
            requiredTags = emptyList(),
            optionalTags = emptyList(),
            forbiddenTags = listOf("pet")
        )

        assertEquals(1, results.size)
        assertEquals("wildcat.jpg", results[0].filename)
    }

    @Test
    fun `should handle complex multi-tag scenario`() {
        // Find all indoor pets that are not dogs
        val results = imageService.searchImages(
            requiredTags = listOf("pet", "indoor"),
            optionalTags = emptyList(),
            forbiddenTags = listOf("dog")
        )

        assertEquals(2, results.size)
        assertTrue(results.any { it.filename == "cat1.jpg" })
        assertTrue(results.any { it.filename == "bird.jpg" })
    }

    @Test
    fun `should find images with any of multiple optional tags`() {
        val results = imageService.searchImages(
            requiredTags = emptyList(),
            optionalTags = listOf("bird", "wild"),
            forbiddenTags = emptyList()
        )

        // With new behavior (optional tags for ranking only), all images should be returned
        // ranked by how many optional tags they match
        assertEquals(5, results.size)
        // First results should be images matching optional tags: bird.jpg (has "bird"), wildcat.jpg (has "wild")
        assertTrue(listOf("bird.jpg", "wildcat.jpg").contains(results[0].filename))
        assertTrue(listOf("bird.jpg", "wildcat.jpg").contains(results[1].filename))
        // Followed by others that don't match any optional tags
    }
}
