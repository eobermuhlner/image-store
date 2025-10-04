package ch.obermuhlner.imagestore.dto

import java.time.Instant

data class SignedUrlRequest(
    val expiresIn: Long?  // Optional: seconds until expiration
)

data class SignedUrlResponse(
    val url: String,
    val expiresAt: Instant
)
