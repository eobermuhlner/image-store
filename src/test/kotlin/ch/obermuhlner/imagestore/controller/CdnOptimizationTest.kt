package ch.obermuhlner.imagestore.controller

import ch.obermuhlner.imagestore.service.ImageService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@Transactional
@TestPropertySource(properties = ["imagestore.security.enabled=false"])
class CdnOptimizationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var imageService: ImageService

    @Test
    fun `should return ETag header`() {
        val image = imageService.storeImage(
            data = "test content".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        val result = mockMvc.perform(get("/api/images/${image.id}"))
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andReturn()

        val etag = result.response.getHeader(HttpHeaders.ETAG)
        assertNotNull(etag)
        assertTrue(etag!!.startsWith("\"") && etag.endsWith("\""))
    }

    @Test
    fun `should return Cache-Control header with max-age`() {
        val image = imageService.storeImage(
            data = "test".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        mockMvc.perform(get("/api/images/${image.id}"))
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.CACHE_CONTROL))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("max-age")))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("public")))
    }

    @Test
    fun `should return Content-Type header`() {
        val image = imageService.storeImage(
            data = "test".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        mockMvc.perform(get("/api/images/${image.id}"))
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/jpeg"))
    }

    @Test
    fun `should return Content-Disposition header`() {
        val image = imageService.storeImage(
            data = "test".toByteArray(),
            filename = "myimage.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        mockMvc.perform(get("/api/images/${image.id}"))
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.CONTENT_DISPOSITION))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("inline")))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("myimage.jpg")))
    }

    @Test
    fun `should return 304 Not Modified when If-None-Match matches ETag`() {
        val image = imageService.storeImage(
            data = "test content".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        // First request to get ETag
        val result = mockMvc.perform(get("/api/images/${image.id}"))
            .andExpect(status().isOk)
            .andReturn()

        val etag = result.response.getHeader(HttpHeaders.ETAG)
        assertNotNull(etag)

        // Second request with If-None-Match
        mockMvc.perform(
            get("/api/images/${image.id}")
                .header(HttpHeaders.IF_NONE_MATCH, etag)
        )
            .andExpect(status().isNotModified)
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(header().exists(HttpHeaders.CACHE_CONTROL))
            .andExpect(content().string(""))
    }

    @Test
    fun `should return 304 Not Modified when If-Modified-Since is in future`() {
        val image = imageService.storeImage(
            data = "test".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        // Request with If-Modified-Since in the future
        val futureDate = "2099-12-31T23:59:59"
        mockMvc.perform(
            get("/api/images/${image.id}")
                .header(HttpHeaders.IF_MODIFIED_SINCE, futureDate)
        )
            .andExpect(status().isNotModified)
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(header().exists(HttpHeaders.CACHE_CONTROL))
    }

    @Test
    fun `should return full response when If-None-Match does not match`() {
        val image = imageService.storeImage(
            data = "test".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        mockMvc.perform(
            get("/api/images/${image.id}")
                .header(HttpHeaders.IF_NONE_MATCH, "\"wrong-etag\"")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(content().bytes("test".toByteArray()))
    }

    @Test
    fun `should return full response when If-Modified-Since is old`() {
        val image = imageService.storeImage(
            data = "test".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        mockMvc.perform(
            get("/api/images/${image.id}")
                .header(HttpHeaders.IF_MODIFIED_SINCE, "2000-01-01T00:00:00")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/jpeg"))
    }

    @Test
    fun `should prioritize ETag over If-Modified-Since`() {
        val image = imageService.storeImage(
            data = "test content".toByteArray(),
            filename = "test.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        val result = mockMvc.perform(get("/api/images/${image.id}"))
            .andReturn()

        val etag = result.response.getHeader(HttpHeaders.ETAG)

        // Both headers present, ETag should take precedence
        mockMvc.perform(
            get("/api/images/${image.id}")
                .header(HttpHeaders.IF_NONE_MATCH, etag)
                .header(HttpHeaders.IF_MODIFIED_SINCE, "2000-01-01T00:00:00")
        )
            .andExpect(status().isNotModified)
    }

    @Test
    fun `should generate different ETags for different content`() {
        val image1 = imageService.storeImage(
            data = "content1".toByteArray(),
            filename = "test1.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        val image2 = imageService.storeImage(
            data = "content2".toByteArray(),
            filename = "test2.jpg",
            contentType = "image/jpeg",
            tags = emptyList()
        )

        val result1 = mockMvc.perform(get("/api/images/${image1.id}"))
            .andReturn()
        val result2 = mockMvc.perform(get("/api/images/${image2.id}"))
            .andReturn()

        val etag1 = result1.response.getHeader(HttpHeaders.ETAG)
        val etag2 = result2.response.getHeader(HttpHeaders.ETAG)

        assertTrue(etag1 != etag2)
    }
}
