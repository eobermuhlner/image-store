package ch.obermuhlner.imagestore.dto

import java.time.Instant

data class SignedUrlRequest(
    val expiresIn: Long?  // Optional: seconds until expiration
)

data class SignedUrlResponse(
    val url: String,
    val expiresAt: Instant
)

data class TokenResponse(
    val token: String,
    val imageId: Long,
    val createdAt: Instant,
    val active: Boolean
)

data class TokenInfo(
    val id: Long,
    val token: String,
    val imageId: Long,
    val createdAt: Instant,
    val active: Boolean
)
