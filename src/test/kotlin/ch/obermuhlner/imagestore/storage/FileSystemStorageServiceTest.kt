package ch.obermuhlner.imagestore.storage

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileSystemStorageServiceTest {

    private lateinit var storageService: FileSystemStorageService
    private val testBasePath = "./test-storage"

    @BeforeEach
    fun setup() {
        storageService = FileSystemStorageService(testBasePath)
    }

    @AfterEach
    fun cleanup() {
        val path = Paths.get(testBasePath)
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `should store and retrieve file`() {
        val data = "test content".toByteArray()
        val path = storageService.store(data, "test.txt")

        assertNotNull(path)
        val retrieved = storageService.retrieve(path)
        assertContentEquals(data, retrieved)
    }

    @Test
    fun `should delete file`() {
        val data = "test content".toByteArray()
        val path = storageService.store(data, "test.txt")

        storageService.delete(path)

        assertThrows<FileNotFoundException> {
            storageService.retrieve(path)
        }
    }

    @Test
    fun `should throw exception for non-existent file`() {
        assertThrows<FileNotFoundException> {
            storageService.retrieve("non-existent.txt")
        }
    }

    @Test
    fun `should generate unique paths for same filename`() {
        val data = "test".toByteArray()
        val path1 = storageService.store(data, "same.txt")
        val path2 = storageService.store(data, "same.txt")

        assertTrue(path1 != path2)
    }
}
