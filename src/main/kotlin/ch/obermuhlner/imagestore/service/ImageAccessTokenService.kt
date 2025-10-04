package ch.obermuhlner.imagestore.service

import ch.obermuhlner.imagestore.model.ImageAccessToken
import ch.obermuhlner.imagestore.repository.ImageAccessTokenRepository
import ch.obermuhlner.imagestore.security.ApiKeyAuthentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ImageAccessTokenService(
    private val tokenRepository: ImageAccessTokenRepository
) {

    fun generateToken(imageId: Long): ImageAccessToken {
        // Get API key ID from authenticated user if available
        val createdByApiKeyId = (SecurityContextHolder.getContext().authentication as? ApiKeyAuthentication)
            ?.apiKey?.id

        val token = ImageAccessToken(
            imageId = imageId,
            createdByApiKeyId = createdByApiKeyId
        )

        return tokenRepository.save(token)
    }

    fun validateToken(imageId: Long, token: String): Boolean {
        return tokenRepository.findByTokenAndActiveTrue(token)
            .map { it.imageId == imageId }
            .orElse(false)
    }

    fun revokeToken(token: String) {
        tokenRepository.findByTokenAndActiveTrue(token)
            .ifPresent { accessToken ->
                accessToken.active = false
                tokenRepository.save(accessToken)
            }
    }

    fun revokeAllTokensForImage(imageId: Long): Int {
        val tokens = tokenRepository.findByImageIdAndActiveTrue(imageId)
        tokens.forEach { it.active = false }
        tokenRepository.saveAll(tokens)
        return tokens.size
    }

    fun listAllTokens(): List<ImageAccessToken> {
        return tokenRepository.findByActiveTrue()
    }

    fun listTokensForImage(imageId: Long): List<ImageAccessToken> {
        return tokenRepository.findByImageIdAndActiveTrue(imageId)
    }
}
