package ch.obermuhlner.imagestore.controller

import ch.obermuhlner.imagestore.storage.StorageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/health")
class HealthController(
    private val storageService: StorageService
) {

    @GetMapping
    fun health(): ResponseEntity<HealthResponse> {
        val storageStatus = checkStorageHealth()

        val overallStatus = if (storageStatus == "healthy") "healthy" else "unhealthy"

        return ResponseEntity.ok(HealthResponse(
            status = overallStatus,
            timestamp = LocalDateTime.now(),
            storage = storageStatus
        ))
    }

    private fun checkStorageHealth(): String {
        return try {
            // Test storage by attempting a simple operation
            val testData = "health-check".toByteArray()
            val testPath = storageService.store(testData, "health-check.txt")
            storageService.retrieve(testPath)
            storageService.delete(testPath)
            "healthy"
        } catch (e: Exception) {
            "unhealthy: ${e.message}"
        }
    }
}

data class HealthResponse(
    val status: String,
    val timestamp: LocalDateTime,
    val storage: String
)
