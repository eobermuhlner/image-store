package ch.obermuhlner.imagestore.controller

import ch.obermuhlner.imagestore.service.ImageService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ImageControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var imageService: ImageService

    @Test
    fun `should upload image with tags`() {
        val file = MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            "test image content".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/images")
                .file(file)
                .param("tags", "nature", "landscape")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.filename").value("test.jpg"))
            .andExpect(jsonPath("$.contentType").value("image/jpeg"))
            .andExpect(jsonPath("$.tags.length()").value(2))
    }

    @Test
    fun `should reject non-image files`() {
        val file = MockMultipartFile(
            "file",
            "test.txt",
            "text/plain",
            "test content".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/images")
                .file(file)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `should retrieve uploaded image`() {
        val imageData = "test image content".toByteArray()
        val image = imageService.storeImage(
            data = imageData,
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = listOf("test")
        )

        mockMvc.perform(get("/api/images/${image.id}"))
            .andExpect(status().isOk)
            .andExpect(header().exists("ETag"))
            .andExpect(header().exists("Cache-Control"))
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(content().bytes(imageData))
    }

    @Test
    fun `should return 304 when ETag matches`() {
        val imageData = "test image content".toByteArray()
        val image = imageService.storeImage(
            data = imageData,
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        val result = mockMvc.perform(get("/api/images/${image.id}"))
            .andExpect(status().isOk)
            .andReturn()

        val etag = result.response.getHeader("ETag")

        mockMvc.perform(
            get("/api/images/${image.id}")
                .header("If-None-Match", etag)
        )
            .andExpect(status().isNotModified)
    }

    @Test
    fun `should get image metadata`() {
        val image = imageService.storeImage(
            data = "test".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = listOf("tag1", "tag2")
        )

        mockMvc.perform(get("/api/images/${image.id}/metadata"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(image.id))
            .andExpect(jsonPath("$.filename").value("test.jpg"))
            .andExpect(jsonPath("$.tags.length()").value(2))
    }

    @Test
    fun `should search images by required tags`() {
        imageService.storeImage(
            data = "image1".toByteArray(),
            filename = "img1.jpg",
            contentType = "image/jpeg",
            tags = listOf("cat", "pet")
        )

        imageService.storeImage(
            data = "image2".toByteArray(),
            filename = "img2.jpg",
            contentType = "image/jpeg",
            tags = listOf("cat", "wild")
        )

        mockMvc.perform(
            get("/api/images/search")
                .param("required", "cat", "pet")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].filename").value("img1.jpg"))
    }

    @Test
    fun `should search images excluding forbidden tags`() {
        imageService.storeImage(
            data = "image1".toByteArray(),
            filename = "img1.jpg",
            contentType = "image/jpeg",
            tags = listOf("dog", "indoor")
        )

        imageService.storeImage(
            data = "image2".toByteArray(),
            filename = "img2.jpg",
            contentType = "image/jpeg",
            tags = listOf("dog", "outdoor")
        )

        mockMvc.perform(
            get("/api/images/search")
                .param("required", "dog")
                .param("forbidden", "indoor")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].filename").value("img2.jpg"))
    }

    @Test
    fun `should delete image`() {
        val image = imageService.storeImage(
            data = "test".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        mockMvc.perform(delete("/api/images/${image.id}"))
            .andExpect(status().isNoContent)

        // Verify image is deleted by checking metadata endpoint
        mockMvc.perform(get("/api/images/${image.id}/metadata"))
            .andExpect(status().isNotFound)
    }
}
