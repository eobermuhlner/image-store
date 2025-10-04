# Image Store

A REST API service for storing and retrieving images with metadata and tag-based search capabilities. Designed for integration with AI pipelines and other applications that need reliable image storage.

## Features

- **Multiple Storage Backends**: Filesystem, Database (H2/PostgreSQL), or AWS S3
- **Tag-Based Search**: Advanced filtering with required, optional, and forbidden tags
- **CDN-Ready**: Built-in caching headers (ETag, Cache-Control) for optimal performance
- **Validation**: File size limits (10MB) and content-type validation
- **Health Monitoring**: Endpoint to verify storage backend availability

## Quick Start

### Prerequisites

- Java 17 or later
- Gradle 8.14+ (or use included wrapper)

### Running the Application

```bash
# Development mode with H2 in-memory database
./gradlew bootRun

# The service will be available at http://localhost:8080
```

### Running Tests

```bash
./gradlew test

# View test report at build/reports/tests/test/index.html
```

## Configuration

Configure the storage backend in `src/main/resources/application.yml`:

### Filesystem Storage (Default)

```yaml
imagestore:
  storage:
    type: filesystem
    filesystem:
      base-path: ./data/images
```

### Database Storage

```yaml
imagestore:
  storage:
    type: database
```

Images are stored as BLOBs in the configured database (H2 for development, PostgreSQL for production).

### S3 Storage

```yaml
imagestore:
  storage:
    type: s3
    s3:
      bucket: your-bucket-name
      region: us-east-1
      endpoint: https://s3.amazonaws.com  # Optional: for S3-compatible services
```

Set AWS credentials via environment variables:
```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
```

### Cache Configuration

```yaml
imagestore:
  cache:
    max-age: 31536000  # seconds (default: 1 year)
    enable-etag: true
```

## API Endpoints

### Upload Image

```bash
curl -X POST http://localhost:8080/api/images \
  -F "file=@image.jpg" \
  -F "tags=nature,landscape,sunset"
```

**Response:**
```json
{
  "id": 1,
  "filename": "image.jpg",
  "contentType": "image/jpeg",
  "size": 245678,
  "uploadDate": "2025-10-04T12:00:00",
  "tags": ["nature", "landscape", "sunset"]
}
```

### Retrieve Image

```bash
curl http://localhost:8080/api/images/1 -o downloaded.jpg
```

Returns the image with CDN-optimized headers (ETag, Cache-Control, Last-Modified).

### Get Image Metadata

```bash
curl http://localhost:8080/api/images/1/metadata
```

**Response:**
```json
{
  "id": 1,
  "filename": "image.jpg",
  "contentType": "image/jpeg",
  "size": 245678,
  "uploadDate": "2025-10-04T12:00:00",
  "tags": ["nature", "landscape", "sunset"]
}
```

### Search Images by Tags

```bash
# Find images with "nature" AND "landscape", optionally "sunset", but NOT "urban"
curl "http://localhost:8080/api/images/search?required=nature,landscape&optional=sunset&forbidden=urban"
```

**Tag Search Logic:**
- `required`: Images must have ALL these tags (AND)
- `optional`: Images should have ANY of these tags (OR)
- `forbidden`: Images must NOT have ANY of these tags (NOT)

### Delete Image

```bash
curl -X DELETE http://localhost:8080/api/images/1
```

### Health Check

```bash
curl http://localhost:8080/api/health
```

**Response:**
```json
{
  "status": "healthy",
  "timestamp": "2025-10-04T12:00:00",
  "storage": "healthy"
}
```

## Supported Image Formats

- JPEG/JPG
- PNG
- GIF
- WebP
- SVG
- BMP
- TIFF

## Production Deployment

### PostgreSQL Configuration

Update `application.yml` for production:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/imagestore
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway/Liquibase for migrations
```

### File Size Limits

Configured in `application.yml`:

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

The application also validates file sizes at the controller level (10MB default).

## Development

### Project Structure

```
src/main/kotlin/ch/obermuhlner/imagestore/
├── config/          # Configuration beans
├── controller/      # REST endpoints
├── dto/             # Data transfer objects
├── exception/       # Global exception handling
├── model/           # JPA entities
├── repository/      # Data access layer
├── service/         # Business logic
└── storage/         # Storage backend implementations
```

### Adding a New Storage Backend

1. Implement the `StorageService` interface
2. Add a conditional bean in `StorageConfig`
3. Add configuration properties to `StorageProperties`
4. Write tests extending the storage test suite

### Test Coverage

Current coverage: **56 tests, 100% passing**

Run tests with coverage report:

```bash
./gradlew test jacocoTestReport

# View report at build/reports/jacoco/test/html/index.html
```

## Error Handling

The API returns structured error responses:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "File cannot be empty",
  "timestamp": "2025-10-04T12:00:00"
}
```

**HTTP Status Codes:**
- `201` - Image uploaded successfully
- `200` - Request successful
- `304` - Not Modified (cache hit)
- `400` - Bad Request (validation error)
- `404` - Not Found
- `413` - Payload Too Large
- `500` - Internal Server Error

## License

[Specify your license here]

## Contributing

[Specify contribution guidelines here]
