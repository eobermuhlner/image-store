package ch.obermuhlner.imagestore.controller

import ch.obermuhlner.imagestore.dto.ApiKeyInfo
import ch.obermuhlner.imagestore.dto.CreateApiKeyRequest
import ch.obermuhlner.imagestore.dto.CreateApiKeyResponse
import ch.obermuhlner.imagestore.model.Permission
import ch.obermuhlner.imagestore.service.ApiKeyService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/admin/keys")
@Tag(name = "API Keys", description = "API key management operations")
class ApiKeyController(
    private val apiKeyService: ApiKeyService
) {

    @PostMapping
    @Operation(summary = "Create API key", description = "Create a new API key with specified permissions. The key is only shown once!")
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "API key created successfully"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun createApiKey(@RequestBody request: CreateApiKeyRequest): ResponseEntity<CreateApiKeyResponse> {
        val (rawKey, apiKey) = apiKeyService.createApiKey(
            request.name,
            request.permissions
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            CreateApiKeyResponse(
                id = apiKey.id!!,
                name = apiKey.name,
                key = rawKey,  // Only shown once!
                permissions = apiKey.permissions,
                createdAt = apiKey.createdAt
            )
        )
    }

    @GetMapping
    @Operation(summary = "List API keys", description = "List all API keys (keys themselves are not returned)")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "API keys retrieved successfully"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun listApiKeys(): ResponseEntity<List<ApiKeyInfo>> {
        val keys = apiKeyService.listApiKeys().map { key ->
            ApiKeyInfo(
                id = key.id!!,
                name = key.name,
                permissions = key.permissions,
                createdAt = key.createdAt,
                lastUsedAt = key.lastUsedAt,
                active = key.active
            )
        }
        return ResponseEntity.ok(keys)
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke API key", description = "Deactivate an API key (soft delete)")
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "API key revoked successfully"),
        ApiResponse(responseCode = "404", description = "API key not found"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun revokeApiKey(@PathVariable id: Long): ResponseEntity<Void> {
        apiKeyService.revokeApiKey(id)
        return ResponseEntity.noContent().build()
    }
}
