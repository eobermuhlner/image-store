package ch.obermuhlner.imagestore.repository

import ch.obermuhlner.imagestore.model.ApiKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ApiKeyRepository : JpaRepository<ApiKey, Long> {
    fun findByActiveTrue(): List<ApiKey>
}
