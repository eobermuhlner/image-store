package ch.obermuhlner.imagestore.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@TestPropertySource(properties = ["imagestore.security.enabled=false"])
class ValidationAndErrorHandlingTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should reject empty file`() {
        val file = MockMultipartFile(
            "file",
            "test.jpg",
            "image/jpeg",
            ByteArray(0)
        )

        mockMvc.perform(
            multipart("/api/images")
                .file(file)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.error").value("Bad Request"))
            .andExpect(jsonPath("$.message").value("File cannot be empty"))
    }

    @Test
    fun `should reject non-image file`() {
        val file = MockMultipartFile(
            "file",
            "document.pdf",
            "application/pdf",
            "test content".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/images")
                .file(file)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should reject text file`() {
        val file = MockMultipartFile(
            "file",
            "document.txt",
            "text/plain",
            "test content".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/images")
                .file(file)
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("Bad Request"))
    }

    @Test
    fun `should accept valid JPEG image`() {
        val file = MockMultipartFile(
            "file",
            "image.jpg",
            "image/jpeg",
            "fake jpeg content".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/images")
                .file(file)
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `should accept valid PNG image`() {
        val file = MockMultipartFile(
            "file",
            "image.png",
            "image/png",
            "fake png content".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/images")
                .file(file)
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `should accept valid WebP image`() {
        val file = MockMultipartFile(
            "file",
            "image.webp",
            "image/webp",
            "fake webp content".toByteArray()
        )

        mockMvc.perform(
            multipart("/api/images")
                .file(file)
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `should return 404 for non-existent image`() {
        mockMvc.perform(get("/api/images/99999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").exists())
    }

    @Test
    fun `should return 404 for non-existent image metadata`() {
        mockMvc.perform(get("/api/images/99999/metadata"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    fun `should return structured error response`() {
        mockMvc.perform(get("/api/images/99999"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.error").exists())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `should return health check endpoint`() {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").exists())
            .andExpect(jsonPath("$.storage").exists())
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `should report healthy storage`() {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("healthy"))
            .andExpect(jsonPath("$.storage").value("healthy"))
    }
}
