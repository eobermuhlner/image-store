package ch.obermuhlner.imagestore.storage

interface StorageService {
    fun store(data: ByteArray, filename: String): String
    fun retrieve(path: String): ByteArray
    fun delete(path: String)
}
