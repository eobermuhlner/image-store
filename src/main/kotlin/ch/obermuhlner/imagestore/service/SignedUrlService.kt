package ch.obermuhlner.imagestore.service

import ch.obermuhlner.imagestore.dto.SignedUrlResponse
import ch.obermuhlner.imagestore.security.SignedUrlValidator
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class SignedUrlService(
    private val signedUrlValidator: SignedUrlValidator,
    @Value("\${imagestore.security.signed-url.default-expiry:3600}")
    private val defaultExpiry: Long,
    @Value("\${imagestore.security.signed-url.max-expiry:604800}")
    private val maxExpiry: Long
) {

    fun generateSignedUrl(imageId: Long, expiresIn: Long?): SignedUrlResponse {
        val expiry = (expiresIn ?: defaultExpiry).coerceAtMost(maxExpiry)
        val expiresAt = Instant.now().epochSecond + expiry
        val signature = signedUrlValidator.generateSignature(imageId, expiresAt)

        return SignedUrlResponse(
            url = "/api/images/$imageId?signature=$signature&expires=$expiresAt",
            expiresAt = Instant.ofEpochSecond(expiresAt)
        )
    }

    fun validateSignedUrl(imageId: Long, signature: String, expires: Long): Boolean {
        return signedUrlValidator.validateSignature(imageId, signature, expires)
    }
}
