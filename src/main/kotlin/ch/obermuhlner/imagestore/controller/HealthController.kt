package ch.obermuhlner.imagestore.controller

import ch.obermuhlner.imagestore.storage.StorageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/health")
@Tag(name = "Health", description = "Health check operations")
class HealthController(
    private val storageService: StorageService
) {

    @GetMapping
    @Operation(summary = "Health check", description = "Check the health status of the API and storage backend")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Health status retrieved", content = [Content(schema = Schema(implementation = HealthResponse::class))])
    ])
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
