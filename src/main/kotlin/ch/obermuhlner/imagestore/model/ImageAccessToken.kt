package ch.obermuhlner.imagestore.model

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "image_access_tokens", indexes = [
    Index(name = "idx_token", columnList = "token"),
    Index(name = "idx_image_id_active", columnList = "image_id,active")
])
data class ImageAccessToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true, length = 36)
    val token: String = UUID.randomUUID().toString(),

    @Column(name = "image_id", nullable = false)
    val imageId: Long,

    @Column(name = "created_by_api_key_id", nullable = true)
    val createdByApiKeyId: Long? = null,

    @Column(nullable = false)
    var active: Boolean = true,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
