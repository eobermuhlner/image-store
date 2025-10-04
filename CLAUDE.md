# Image Store Project

REST API service for storing and retrieving images with metadata and tag-based search.

## Architecture

**Storage Backends** (configurable via `imagestore.storage.type`):
- `filesystem` - stores files in local directory
- `database` - stores files as BLOBs in H2/PostgreSQL
- `s3` - stores files in AWS S3 or compatible services

**Domain Model**:
- `Image` entity with many-to-many relationship to `Tag`
- Tag-based search with AND/OR/NOT logic using JPQL

**Key Features**:
- Multipart file upload with validation (10MB limit, image types only)
- CDN optimization (ETag, Cache-Control, conditional GET with If-None-Match)
- Global exception handling with structured error responses
- Health check endpoint that tests storage backend

## Endpoints

- `POST /api/images` - upload image with optional tags
- `GET /api/images/{id}` - retrieve image with CDN headers
- `GET /api/images/{id}/metadata` - get image metadata
- `GET /api/images/search?required=tag1&optional=tag2&forbidden=tag3` - search by tags
- `DELETE /api/images/{id}` - delete image
- `GET /api/health` - storage backend health check

## Testing Requirements
- Maintain test coverage >80%
- Run tests after every development step: `./gradlew test`
- Current: 56 tests passing (100%)

## Development Principles
- Keep it simple - avoid overengineering
- Prefer straightforward solutions over complex abstractions
