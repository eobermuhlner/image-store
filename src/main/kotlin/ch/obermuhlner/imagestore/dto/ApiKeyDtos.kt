package ch.obermuhlner.imagestore.dto

import ch.obermuhlner.imagestore.model.Permission
import java.time.Instant

data class CreateApiKeyRequest(
    val name: String,
    val permissions: Set<Permission>
)

data class CreateApiKeyResponse(
    val id: Long,
    val name: String,
    val key: String,  // Only shown once during creation!
    val permissions: Set<Permission>,
    val createdAt: Instant
)

data class ApiKeyInfo(
    val id: Long,
    val name: String,
    val permissions: Set<Permission>,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val active: Boolean
)
