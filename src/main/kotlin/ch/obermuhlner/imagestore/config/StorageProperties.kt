package ch.obermuhlner.imagestore.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "imagestore")
data class StorageProperties(
    var storage: StorageTypeConfig = StorageTypeConfig(),
    var cache: CacheConfig = CacheConfig()
)

data class StorageTypeConfig(
    var type: String = "filesystem",
    var filesystem: FileSystemConfig = FileSystemConfig(),
    var s3: S3Config = S3Config()
)

data class FileSystemConfig(
    var basePath: String = "./data/images"
)

data class S3Config(
    var bucket: String = "",
    var region: String = "us-east-1",
    var endpoint: String? = null
)

data class CacheConfig(
    var maxAge: Int = 31536000,
    var enableEtag: Boolean = true
)
