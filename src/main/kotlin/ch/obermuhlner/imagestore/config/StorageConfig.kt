package ch.obermuhlner.imagestore.config

import ch.obermuhlner.imagestore.storage.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = ["imagestore.storage.type"], havingValue = "filesystem", matchIfMissing = true)
    fun fileSystemStorageService(storageProperties: StorageProperties): StorageService {
        return FileSystemStorageService(storageProperties.filesystem.basePath)
    }

    @Bean
    @ConditionalOnProperty(name = ["imagestore.storage.type"], havingValue = "database")
    fun databaseStorageService(storedFileRepository: StoredFileRepository): StorageService {
        return DatabaseStorageService(storedFileRepository)
    }

    @Bean
    @ConditionalOnProperty(name = ["imagestore.storage.type"], havingValue = "s3")
    fun s3StorageService(storageProperties: StorageProperties): StorageService {
        return S3StorageService(
            bucketName = storageProperties.s3.bucket,
            region = storageProperties.s3.region,
            endpoint = storageProperties.s3.endpoint
        )
    }
}
