# Image Store

A REST API service for storing and retrieving images with metadata and tag-based search capabilities. Designed for integration with AI pipelines and other applications that need reliable image storage.

## Features

- **Multiple Storage Backends**: Filesystem, Database (H2/PostgreSQL), or AWS S3
- **Authentication & Authorization**: API key-based authentication with permission-based access control
- **Signed URLs**: Time-limited HMAC-signed URLs for secure public image access
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

### Security Configuration

Authentication and authorization are **disabled by default**. To enable:

```yaml
imagestore:
  security:
    enabled: true  # Enable authentication/authorization
    secret-key: your-long-random-secret-key-change-in-production  # HMAC secret for signed URLs
    signed-url:
      default-expiry-seconds: 3600   # 1 hour
      max-expiry-seconds: 604800      # 7 days
```

**Important**: When you first start the application with security enabled and no API keys exist, an initial admin key will be automatically generated and printed to the console. **Save this key** - it won't be shown again!

#### Permissions

API keys can have the following permissions:
- `UPLOAD` - Upload new images
- `DELETE` - Delete images
- `SEARCH` - Search and list images, get metadata
- `GENERATE_SIGNED_URL` - Create time-limited signed URLs for public access
- `ADMIN` - Manage API keys (create, list, revoke)

## API Endpoints

### Authentication

When security is enabled, most endpoints require authentication via Bearer token:

```bash
curl -H "Authorization: Bearer YOUR_API_KEY" \
  http://localhost:8080/api/images
```

### Admin Endpoints (Security Enabled Only)

#### Create API Key

```bash
curl -X POST http://localhost:8080/api/admin/keys \
  -H "Authorization: Bearer YOUR_ADMIN_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Upload Service Key",
    "permissions": ["UPLOAD", "SEARCH"]
  }'
```

**Response:**
```json
{
  "id": 2,
  "name": "Upload Service Key",
  "key": "sk_1a2b3c4d5e6f7g8h9i0j...",
  "permissions": ["UPLOAD", "SEARCH"],
  "createdAt": "2025-10-04T12:00:00"
}
```

**Note**: The API key is only shown once during creation!

#### List API Keys

```bash
curl http://localhost:8080/api/admin/keys \
  -H "Authorization: Bearer YOUR_ADMIN_API_KEY"
```

#### Revoke API Key

```bash
curl -X DELETE http://localhost:8080/api/admin/keys/2 \
  -H "Authorization: Bearer YOUR_ADMIN_API_KEY"
```

### Upload Image

```bash
# Without security
curl -X POST http://localhost:8080/api/images \
  -F "file=@image.jpg" \
  -F "tags=nature,landscape,sunset"

# With security enabled
curl -X POST http://localhost:8080/api/images \
  -H "Authorization: Bearer YOUR_API_KEY" \
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
# Without security (or with signed URL)
curl http://localhost:8080/api/images/1 -o downloaded.jpg

# With security enabled (authenticated)
curl -H "Authorization: Bearer YOUR_API_KEY" \
  http://localhost:8080/api/images/1 -o downloaded.jpg

# With security enabled (using signed URL - no auth needed)
curl "http://localhost:8080/api/images/1?signature=abc123&expires=1234567890" \
  -o downloaded.jpg
```

Returns the image with CDN-optimized headers (ETag, Cache-Control, Last-Modified).

### Generate Signed URL (Security Enabled Only)

Create a time-limited URL for public access to an image:

```bash
curl -X POST http://localhost:8080/api/images/1/sign \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"expiresIn": 3600}'
```

**Response:**
```json
{
  "url": "/api/images/1?signature=xyz789&expires=1234567890",
  "expiresAt": "2025-10-04T13:00:00Z"
}
```

### Generate Permanent Access Token (Security Enabled Only)

Create a permanent token for long-term access (perfect for AI chat applications):

```bash
curl -X POST http://localhost:8080/api/images/1/token \
  -H "Authorization: Bearer YOUR_API_KEY"
```

**Response:**
```json
{
  "token": "abc-123-uuid-here",
  "imageId": 1,
  "createdAt": "2025-10-04T12:00:00",
  "active": true
}
```

**Use the token to access the image:**
```bash
# No authentication needed - token never expires
curl "http://localhost:8080/api/images/1?token=abc-123-uuid-here" \
  -o downloaded.jpg
```

**Revoke token when needed:**
```bash
curl -X DELETE http://localhost:8080/api/admin/tokens/abc-123-uuid-here \
  -H "Authorization: Bearer YOUR_ADMIN_API_KEY"
```

**Use case:** Store token in AI chat messages for permanent image access with ability to revoke if needed.

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
- `204` - No Content (successful deletion)
- `304` - Not Modified (cache hit)
- `400` - Bad Request (validation error)
- `403` - Forbidden (insufficient permissions or invalid authentication)
- `404` - Not Found
- `413` - Payload Too Large
- `500` - Internal Server Error

## License

[Specify your license here]

## Contributing

[Specify contribution guidelines here]
