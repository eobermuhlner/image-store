package ch.obermuhlner.imagestore.storage

import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Entity
@Table(name = "stored_files")
class StoredFile(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Lob
    @Column(nullable = false, length = 10485760)
    val data: ByteArray = ByteArray(0)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredFile) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Repository
interface StoredFileRepository : JpaRepository<StoredFile, String>

class DatabaseStorageService(
    private val repository: StoredFileRepository
) : StorageService {

    override fun store(data: ByteArray, filename: String): String {
        val storedFile = StoredFile(data = data)
        repository.save(storedFile)
        return storedFile.id
    }

    override fun retrieve(path: String): ByteArray {
        val storedFile = repository.findById(path)
            .orElseThrow { NoSuchElementException("File not found: $path") }
        return storedFile.data
    }

    override fun delete(path: String) {
        repository.deleteById(path)
    }
}
