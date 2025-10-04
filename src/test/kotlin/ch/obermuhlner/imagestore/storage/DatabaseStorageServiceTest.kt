package ch.obermuhlner.imagestore.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull

@DataJpaTest
class DatabaseStorageServiceTest {

    @Autowired
    private lateinit var repository: StoredFileRepository

    @Test
    fun `should store and retrieve file`() {
        val storageService = DatabaseStorageService(repository)
        val data = "test content".toByteArray()

        val id = storageService.store(data, "test.txt")
        assertNotNull(id)

        val retrieved = storageService.retrieve(id)
        assertContentEquals(data, retrieved)
    }

    @Test
    fun `should delete file`() {
        val storageService = DatabaseStorageService(repository)
        val data = "test content".toByteArray()
        val id = storageService.store(data, "test.txt")

        storageService.delete(id)

        assertThrows<NoSuchElementException> {
            storageService.retrieve(id)
        }
    }

    @Test
    fun `should throw exception for non-existent file`() {
        val storageService = DatabaseStorageService(repository)

        assertThrows<NoSuchElementException> {
            storageService.retrieve("non-existent-id")
        }
    }
}
