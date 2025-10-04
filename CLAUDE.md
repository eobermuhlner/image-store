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

**Security** (disabled by default, configurable via `imagestore.security.enabled`):
- API key authentication with BCrypt hashing (Bearer token)
- HMAC-SHA256 signed URLs for time-limited public access
- **Permanent access tokens** (UUID-based, never expire, revocable) - ideal for AI chat applications
- Permission-based authorization (UPLOAD, DELETE, SEARCH, GENERATE_SIGNED_URL, ADMIN)
- Initial admin key auto-generated on first startup when security enabled
- Spring Security with conditional bean loading for backward compatibility
- Token validation adds ~1ms overhead (negligible with CDN caching)

**Key Features**:
- Multipart file upload with validation (10MB limit, image types only)
- CDN optimization (ETag, Cache-Control, conditional GET with If-None-Match)
- Global exception handling with structured error responses
- Health check endpoint that tests storage backend
- OpenAPI 3 specification available at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`

## Endpoints

**Image Operations:**
- `POST /api/images` - upload image with optional tags (requires UPLOAD permission when security enabled)
- `GET /api/images/{id}` - retrieve image with CDN headers (accepts authentication OR signed URL OR access token)
- `GET /api/images/{id}/metadata` - get image metadata (requires SEARCH permission when security enabled)
- `GET /api/images/search?required=tag1&optional=tag2&forbidden=tag3` - search by tags (requires SEARCH permission)
- `DELETE /api/images/{id}` - delete image (requires DELETE permission)
- `POST /api/images/{id}/sign` - generate time-limited signed URL (requires GENERATE_SIGNED_URL permission)
- `POST /api/images/{id}/token` - generate permanent access token (requires GENERATE_SIGNED_URL permission)

**Admin Operations (security enabled only):**
- `POST /api/admin/keys` - create new API key (requires ADMIN permission)
- `GET /api/admin/keys` - list all API keys (requires ADMIN permission)
- `DELETE /api/admin/keys/{id}` - revoke API key (requires ADMIN permission)
- `GET /api/admin/tokens` - list all access tokens (requires ADMIN permission)
- `GET /api/admin/tokens/image/{imageId}` - list tokens for specific image (requires ADMIN permission)
- `DELETE /api/admin/tokens/{token}` - revoke specific access token (requires ADMIN permission)
- `DELETE /api/admin/tokens/image/{imageId}` - revoke all tokens for image (requires ADMIN permission)

**Health & Documentation:**
- `GET /api/health` - storage backend health check (public)
- `GET /v3/api-docs` - OpenAPI specification (public)
- `GET /swagger-ui.html` - Swagger UI (public)

## Testing Requirements
- Maintain test coverage >80%
- Run tests after every development step: `./gradlew test`
- Current: 56 tests passing (100%)

## Development Principles
- Keep it simple - avoid overengineering
- Prefer straightforward solutions over complex abstractions

## Claude Instructions
- **IMPORTANT**: Always keep the following files up to date after making changes:
  - CLAUDE.md (this file)
  - README.md
  - src/test/http/http-client-requests.http
- After implementing new features or endpoints, immediately update all three files before considering the task complete
