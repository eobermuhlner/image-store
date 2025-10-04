package ch.obermuhlner.imagestore.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "images")
data class Image(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val filename: String,

    @Column(nullable = false)
    val contentType: String,

    @Column(nullable = false)
    val size: Long,

    @Column(nullable = false)
    val uploadDate: Instant = Instant.now(),

    @Column(nullable = false)
    val storageType: String,

    @Column(nullable = false)
    val storagePath: String,

    @Column(nullable = true)
    val uploadedByApiKeyId: Long? = null,

    @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST, CascadeType.MERGE])
    @JoinTable(
        name = "image_tags",
        joinColumns = [JoinColumn(name = "image_id")],
        inverseJoinColumns = [JoinColumn(name = "tag_id")]
    )
    val tags: MutableSet<Tag> = mutableSetOf()
)
