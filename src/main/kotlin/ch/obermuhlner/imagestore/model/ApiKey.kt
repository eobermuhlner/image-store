package ch.obermuhlner.imagestore.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "api_keys")
data class ApiKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false, unique = true)
    val keyHash: String,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_permissions", joinColumns = [JoinColumn(name = "api_key_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    val permissions: Set<Permission> = emptySet(),

    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column
    var lastUsedAt: Instant? = null,

    @Column(nullable = false)
    var active: Boolean = true
)
