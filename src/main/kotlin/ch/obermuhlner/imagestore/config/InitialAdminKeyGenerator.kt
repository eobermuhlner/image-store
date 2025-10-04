package ch.obermuhlner.imagestore.config

import ch.obermuhlner.imagestore.model.Permission
import ch.obermuhlner.imagestore.service.ApiKeyService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["imagestore.security.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class InitialAdminKeyGenerator(
    private val apiKeyService: ApiKeyService
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(InitialAdminKeyGenerator::class.java)

    override fun run(args: ApplicationArguments) {
        val existingKeys = apiKeyService.listApiKeys()

        if (existingKeys.isEmpty()) {
            logger.warn("=".repeat(80))
            logger.warn("No API keys found. Generating initial admin key...")

            val (rawKey, apiKey) = apiKeyService.createApiKey(
                "Initial Admin Key",
                setOf(
                    Permission.UPLOAD,
                    Permission.DELETE,
                    Permission.SEARCH,
                    Permission.GENERATE_SIGNED_URL,
                    Permission.ADMIN
                )
            )

            logger.warn("")
            logger.warn("INITIAL ADMIN API KEY GENERATED:")
            logger.warn("  Key ID: ${apiKey.id}")
            logger.warn("  Key Name: ${apiKey.name}")
            logger.warn("  API Key: $rawKey")
            logger.warn("")
            logger.warn("IMPORTANT: This key will NOT be shown again!")
            logger.warn("Save it securely and use it to create additional keys via /api/admin/keys")
            logger.warn("=".repeat(80))
        } else {
            logger.info("Found ${existingKeys.size} existing API key(s). Skipping initial key generation.")
        }
    }
}
