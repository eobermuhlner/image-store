package ch.obermuhlner.imagestore.repository

import ch.obermuhlner.imagestore.model.ImageAccessToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
interface ImageAccessTokenRepository : JpaRepository<ImageAccessToken, Long> {
    fun findByTokenAndActiveTrue(token: String): Optional<ImageAccessToken>
    fun findByImageIdAndActiveTrue(imageId: Long): List<ImageAccessToken>
    fun findByActiveTrue(): List<ImageAccessToken>
}
