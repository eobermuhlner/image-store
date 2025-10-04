package ch.obermuhlner.imagestore.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class SignedUrlValidator(
    @Value("\${imagestore.security.secret-key:default-secret-change-me}")
    private val secretKey: String
) {

    fun generateSignature(imageId: Long, expiresAt: Long): String {
        val data = "$imageId:$expiresAt"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(data.toByteArray()))
    }

    fun validateSignature(imageId: Long, signature: String, expiresAt: Long): Boolean {
        // Check expiration
        if (Instant.now().epochSecond > expiresAt) {
            return false
        }

        // Verify signature using constant-time comparison
        val expected = generateSignature(imageId, expiresAt)
        return MessageDigest.isEqual(
            signature.toByteArray(),
            expected.toByteArray()
        )
    }
}
