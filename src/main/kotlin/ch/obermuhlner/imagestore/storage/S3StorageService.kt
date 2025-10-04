package ch.obermuhlner.imagestore.storage

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.net.URI
import java.util.*

class S3StorageService(
    private val bucketName: String,
    private val region: String,
    private val endpoint: String?
) : StorageService {

    private val s3Client: S3Client = if (endpoint != null) {
        S3Client.builder()
            .region(Region.of(region))
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    } else {
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }

    override fun store(data: ByteArray, filename: String): String {
        val key = "${UUID.randomUUID()}_$filename"
        val putRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        s3Client.putObject(putRequest, RequestBody.fromBytes(data))
        return key
    }

    override fun retrieve(path: String): ByteArray {
        val getRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(path)
            .build()
        return s3Client.getObjectAsBytes(getRequest).asByteArray()
    }

    override fun delete(path: String) {
        val deleteRequest = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(path)
            .build()
        s3Client.deleteObject(deleteRequest)
    }
}
