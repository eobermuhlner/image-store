package ch.obermuhlner.imagestore.repository

import ch.obermuhlner.imagestore.model.Tag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TagRepository : JpaRepository<Tag, Long> {
    fun findByName(name: String): Optional<Tag>
}
