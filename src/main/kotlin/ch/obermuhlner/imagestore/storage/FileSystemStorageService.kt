package ch.obermuhlner.imagestore.storage

import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

class FileSystemStorageService(
    private val basePath: String
) : StorageService {

    init {
        Files.createDirectories(Paths.get(basePath))
    }

    override fun store(data: ByteArray, filename: String): String {
        val uniqueFilename = "${UUID.randomUUID()}_$filename"
        val path = Paths.get(basePath, uniqueFilename)
        Files.write(path, data)
        return uniqueFilename
    }

    override fun retrieve(path: String): ByteArray {
        val filePath = Paths.get(basePath, path)
        if (!Files.exists(filePath)) {
            throw FileNotFoundException("File not found: $path")
        }
        return Files.readAllBytes(filePath)
    }

    override fun delete(path: String) {
        val filePath = Paths.get(basePath, path)
        Files.deleteIfExists(filePath)
    }
}
