package ch.obermuhlner.imagestore.service

import ch.obermuhlner.imagestore.model.ApiKey
import ch.obermuhlner.imagestore.model.Permission
import ch.obermuhlner.imagestore.repository.ApiKeyRepository
import ch.obermuhlner.imagestore.security.ApiKeyAuthentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant

@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val passwordEncoder: PasswordEncoder
) {

    fun createApiKey(name: String, permissions: Set<Permission>): Pair<String, ApiKey> {
        // Generate secure random key
        val rawKey = "sk_live_" + SecureRandom().let { random ->
            ByteArray(32).apply { random.nextBytes(this) }
                .joinToString("") { "%02x".format(it) }
        }

        val apiKey = ApiKey(
            name = name,
            keyHash = passwordEncoder.encode(rawKey),
            permissions = permissions
        )

        val saved = apiKeyRepository.save(apiKey)
        return Pair(rawKey, saved)
    }

    fun authenticate(rawKey: String): ApiKeyAuthentication? {
        // Find all active keys and check against hash
        val apiKeys = apiKeyRepository.findByActiveTrue()

        for (apiKey in apiKeys) {
            if (passwordEncoder.matches(rawKey, apiKey.keyHash)) {
                // Update last used timestamp
                apiKey.lastUsedAt = Instant.now()
                apiKeyRepository.save(apiKey)

                // Create authentication object
                return ApiKeyAuthentication(apiKey)
            }
        }

        return null
    }

    fun revokeApiKey(id: Long) {
        val apiKey = apiKeyRepository.findById(id)
            .orElseThrow { NoSuchElementException("API key not found: $id") }

        apiKey.active = false
        apiKeyRepository.save(apiKey)
    }

    fun listApiKeys(): List<ApiKey> {
        return apiKeyRepository.findAll()
    }
}
