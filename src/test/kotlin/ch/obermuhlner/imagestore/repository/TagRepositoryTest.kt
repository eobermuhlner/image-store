package ch.obermuhlner.imagestore.repository

import ch.obermuhlner.imagestore.model.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DataJpaTest
class TagRepositoryTest {

    @Autowired
    private lateinit var tagRepository: TagRepository

    @Test
    fun `should save and retrieve tag`() {
        val tag = Tag(name = "test-tag")
        val saved = tagRepository.save(tag)

        assertNotNull(saved.id)
        assertEquals("test-tag", saved.name)
    }

    @Test
    fun `should find tag by name`() {
        tagRepository.save(Tag(name = "findme"))

        val found = tagRepository.findByName("findme")
        assertTrue(found.isPresent)
        assertEquals("findme", found.get().name)
    }

    @Test
    fun `should return empty for non-existent tag`() {
        val found = tagRepository.findByName("nonexistent")
        assertTrue(found.isEmpty)
    }
}
