package ch.obermuhlner.imagestore.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
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

        // Should return 1 image that has "dog" tag
        assertEquals(1, results.size)
        assertTrue(results.isNotEmpty(), "Results should not be empty")
        assertTrue(results.all { "dog" in it.tags.map { tag -> tag.name } }, "All results must have 'dog' tag")
    }

    @Test
    fun `should find images with multiple required tags (AND)`() {
        val results = imageService.searchImages(
            requiredTags = listOf("cat", "pet"),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        // Should return images that have both "cat" AND "pet" tags
        assertEquals(2, results.size)
        assertTrue(results.all {
            val tagNames = it.tags.map { tag -> tag.name }
            "cat" in tagNames && "pet" in tagNames
        }, "All results must have both 'cat' and 'pet' tags")
    }

    @Test
    fun `should find images with all three required tags`() {
        val results = imageService.searchImages(
            requiredTags = listOf("cat", "pet", "indoor"),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        assertEquals(1, results.size)
        assertTrue(results.all {
            val tagNames = it.tags.map { tag -> tag.name }
            "cat" in tagNames && "pet" in tagNames && "indoor" in tagNames
        }, "All results must have 'cat', 'pet', and 'indoor' tags")
    }

    @Test
    fun `should find images excluding forbidden tags (NOT)`() {
        val results = imageService.searchImages(
            requiredTags = listOf("cat"),
            optionalTags = emptyList(),
            forbiddenTags = listOf("wild")
        )

        // Should return 2 cat images that have "cat" tag but NOT "wild" tag
        assertEquals(2, results.size)
        assertTrue(results.all {
            val tagNames = it.tags.map { tag -> tag.name }
            "cat" in tagNames && "wild" !in tagNames
        }, "All results must have 'cat' tag but NOT 'wild' tag")
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

        // Count how many images in the first 3 positions have "indoor" tag
        val firstThreeWithIndoorCount = results.take(3).count {
            val tagNames = it.tags.map { tag -> tag.name }
            "indoor" in tagNames
        }

        // Count how many images in the last 2 positions have "indoor" tag
        val lastTwoWithIndoorCount = results.drop(3).count {
            val tagNames = it.tags.map { tag -> tag.name }
            "indoor" in tagNames
        }

        // Verify that the first 3 positions contain all the indoor-tagged images
        assertEquals(3, firstThreeWithIndoorCount, "First 3 results should all have 'indoor' tag")
        assertEquals(0, lastTwoWithIndoorCount, "Last 2 results should not have 'indoor' tag")
    }

    @Test
    fun `should combine required and forbidden tags`() {
        val results = imageService.searchImages(
            requiredTags = listOf("pet"),
            optionalTags = emptyList(),
            forbiddenTags = listOf("dog", "bird")
        )

        assertEquals(2, results.size)
        assertTrue(results.all {
            val tagNames = it.tags.map { tag -> tag.name }
            "pet" in tagNames && "dog" !in tagNames && "bird" !in tagNames
        }, "All results must have 'pet' tag but NOT 'dog' or 'bird' tags")
    }

    @Test
    fun `should combine required, optional, and forbidden tags`() {
        val results = imageService.searchImages(
            requiredTags = listOf("cat"),
            optionalTags = listOf("indoor", "outdoor"),
            forbiddenTags = listOf("wild")
        )

        // Should return images with "cat" tag but NOT "wild" tag, ranked by optional tags
        assertEquals(2, results.size)
        assertTrue(results.all {
            val tagNames = it.tags.map { tag -> tag.name }
            "cat" in tagNames && "wild" !in tagNames
        }, "All results must have 'cat' tag but NOT 'wild' tag")
    }

    @Test
    fun `should return all images when no filters specified`() {
        val results = imageService.searchImages(
            requiredTags = emptyList(),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        // Should return all 5 images when no filters are specified
        assertEquals(5, results.size)
    }

    @Test
    fun `should return empty list when required tags don't match any image`() {
        val results = imageService.searchImages(
            requiredTags = listOf("elephant"),
            optionalTags = emptyList(),
            forbiddenTags = emptyList()
        )

        // Should return empty list when required tag doesn't match any existing image
        assertTrue(results.isEmpty())
    }

    @Test
    fun `should exclude all images with forbidden tag`() {
        val results = imageService.searchImages(
            requiredTags = emptyList(),
            optionalTags = emptyList(),
            forbiddenTags = listOf("pet")
        )

        // Should return only images that don't have the "pet" forbidden tag
        assertEquals(1, results.size)
        assertTrue(results.all {
            val tagNames = it.tags.map { tag -> tag.name }
            "pet" !in tagNames
        }, "All results must NOT have 'pet' tag")
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
        assertTrue(results.all {
            val tagNames = it.tags.map { tag -> tag.name }
            "pet" in tagNames && "indoor" in tagNames && "dog" !in tagNames
        }, "All results must have 'pet' and 'indoor' tags but NOT 'dog' tag")
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

        // Count how many images in the first 2 positions have "bird" or "wild" tag
        val firstTwoWithOptionalCount = results.take(2).count {
            val tagNames = it.tags.map { tag -> tag.name }
            "bird" in tagNames || "wild" in tagNames
        }

        // Count how many images in the remaining positions have "bird" or "wild" tag
        val remainingWithOptionalCount = results.drop(2).count {
            val tagNames = it.tags.map { tag -> tag.name }
            "bird" in tagNames || "wild" in tagNames
        }

        // Verify that the first 2 positions contain all the images with optional tags
        assertEquals(2, firstTwoWithOptionalCount, "First 2 results should have either 'bird' or 'wild' tag")
        assertEquals(0, remainingWithOptionalCount, "Remaining results should not have 'bird' or 'wild' tag")
    }
}
