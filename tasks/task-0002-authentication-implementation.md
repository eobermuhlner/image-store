# Authentication & Authorization Implementation Plan

## Architecture Overview

### Design Goals
- **Simple**: API key-based authentication (no complex OAuth/JWT)
- **Secure**: HMAC-SHA256 signed URLs with expiration
- **Flexible**: Permission-based access control
- **Scalable**: Stateless design (no session storage)
- **Backward Compatible**: Can be disabled for development

### Components

```
┌─────────────────────────────────────────────────┐
│  Layer 1: API Key Authentication                 │
│  - Bearer token validation                       │
│  - Permission checking                           │
│  - Used for: Upload, Delete, Search, Management  │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│  Layer 2: Signed URL Authorization               │
│  - HMAC-SHA256 signature validation             │
│  - Expiration checking                           │
│  - Used for: Time-limited public image access    │
└─────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────┐
│  Layer 3: Resource Access (Optional)             │
│  - Owner-based restrictions                      │
│  - Admin override capabilities                   │
└─────────────────────────────────────────────────┘
```

## Implementation Steps

### 1. Domain Model

**ApiKey Entity:**
```kotlin
@Entity
@Table(name = "api_keys")
data class ApiKey(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,  // "CI/CD Pipeline", "Mobile App"

    @Column(nullable = false, unique = true)
    val keyHash: String,  // BCrypt hash of the API key

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    val permissions: Set<Permission> = emptySet(),

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column
    var lastUsedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var active: Boolean = true
)

enum class Permission {
    UPLOAD,               // Upload new images
    DELETE,               // Delete images
    SEARCH,               // Search and list images
    GENERATE_SIGNED_URL   // Create signed URLs
}
```

**Image Entity Update:**
```kotlin
// Add to existing Image entity
@Column
val uploadedByApiKeyId: Long? = null  // Track who uploaded (optional)
```

### 2. Security Layer

**ApiKeyAuthenticationFilter:**
```kotlin
@Component
class ApiKeyAuthenticationFilter(
    private val apiKeyService: ApiKeyService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader?.startsWith("Bearer ") == true) {
            val apiKey = authHeader.substring(7)
            val authentication = apiKeyService.authenticate(apiKey)

            if (authentication != null) {
                SecurityContextHolder.getContext().authentication = authentication
            }
        }

        filterChain.doFilter(request, response)
    }
}
```

**SignedUrlValidator:**
```kotlin
@Component
class SignedUrlValidator(
    @Value("\${imagestore.security.secret-key}")
    private val secretKey: String
) {
    fun generateSignature(imageId: Long, expiresAt: Long): String {
        val data = "$imageId:$expiresAt"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        return Base64.getUrlEncoder().encodeToString(mac.doFinal(data.toByteArray()))
    }

    fun validateSignature(
        imageId: Long,
        signature: String,
        expiresAt: Long
    ): Boolean {
        // Check expiration
        if (Instant.now().epochSecond > expiresAt) {
            return false
        }

        // Verify signature
        val expected = generateSignature(imageId, expiresAt)
        return MessageDigest.isEqual(
            signature.toByteArray(),
            expected.toByteArray()
        )
    }
}
```

**SecurityConfig:**
```kotlin
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(
    name = ["imagestore.security.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class SecurityConfig(
    private val apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    // Public endpoints (health, swagger)
                    .requestMatchers("/api/health", "/v3/api-docs/**", "/swagger-ui/**")
                    .permitAll()

                    // Image retrieval with signed URLs
                    .requestMatchers(HttpMethod.GET, "/api/images/**")
                    .permitAll()

                    // Protected endpoints
                    .requestMatchers("/api/images", "/api/images/*/sign")
                    .authenticated()

                    .requestMatchers("/api/admin/**")
                    .authenticated()

                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                apiKeyAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java
            )

        return http.build()
    }
}
```

### 3. Services

**ApiKeyService:**
```kotlin
@Service
class ApiKeyService(
    private val apiKeyRepository: ApiKeyRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun createApiKey(name: String, permissions: Set<Permission>): Pair<String, ApiKey> {
        // Generate secure random key
        val rawKey = "sk_live_" + SecureRandom().let { random ->
            ByteArray(32).apply { random.nextBytes(this) }
                .joinToString("") { "%02x".format(it) }
        }

        val apiKey = ApiKey(
            name = name,
            keyHash = passwordEncoder.encode(rawKey),
            permissions = permissions
        )

        val saved = apiKeyRepository.save(apiKey)
        return Pair(rawKey, saved)
    }

    fun authenticate(rawKey: String): Authentication? {
        // Find all active keys and check against hash
        val apiKeys = apiKeyRepository.findByActiveTrue()

        for (apiKey in apiKeys) {
            if (passwordEncoder.matches(rawKey, apiKey.keyHash)) {
                // Update last used timestamp
                apiKey.lastUsedAt = LocalDateTime.now()
                apiKeyRepository.save(apiKey)

                // Create authentication object
                return ApiKeyAuthentication(apiKey)
            }
        }

        return null
    }

    fun hasPermission(authentication: Authentication, permission: Permission): Boolean {
        if (authentication !is ApiKeyAuthentication) return false
        return permission in authentication.apiKey.permissions
    }
}
```

**SignedUrlService:**
```kotlin
@Service
class SignedUrlService(
    private val signedUrlValidator: SignedUrlValidator,
    @Value("\${imagestore.security.signed-url.default-expiry:3600}")
    private val defaultExpiry: Long,
    @Value("\${imagestore.security.signed-url.max-expiry:604800}")
    private val maxExpiry: Long
) {
    fun generateSignedUrl(imageId: Long, expiresIn: Long?): SignedUrlResponse {
        val expiry = (expiresIn ?: defaultExpiry).coerceAtMost(maxExpiry)
        val expiresAt = Instant.now().epochSecond + expiry
        val signature = signedUrlValidator.generateSignature(imageId, expiresAt)

        return SignedUrlResponse(
            url = "/api/images/$imageId?signature=$signature&expires=$expiresAt",
            expiresAt = Instant.ofEpochSecond(expiresAt)
        )
    }

    fun validateRequest(imageId: Long, signature: String?, expires: String?): Boolean {
        if (signature == null || expires == null) {
            return false
        }

        val expiresAt = expires.toLongOrNull() ?: return false
        return signedUrlValidator.validateSignature(imageId, signature, expiresAt)
    }
}
```

### 4. Controllers

**Update ImageController:**
```kotlin
@RestController
@RequestMapping("/api/images")
class ImageController(
    private val imageService: ImageService,
    private val signedUrlService: SignedUrlService,
    private val apiKeyService: ApiKeyService,
    private val storageProperties: StorageProperties
) {
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAuthority('UPLOAD')")
    fun uploadImage(...): ResponseEntity<ImageUploadResponse> {
        // Existing implementation
    }

    @GetMapping("/{id}")
    fun getImage(
        @PathVariable id: Long,
        @RequestParam(required = false) signature: String?,
        @RequestParam(required = false) expires: String?,
        @RequestHeader(value = "If-None-Match", required = false) ifNoneMatch: String?,
        authentication: Authentication?
    ): ResponseEntity<ByteArray> {
        // Check authentication: either API key OR valid signed URL
        val hasAuth = authentication != null && authentication.isAuthenticated
        val hasValidSignature = signedUrlService.validateRequest(id, signature, expires)

        if (!hasAuth && !hasValidSignature) {
            throw AccessDeniedException("Authentication required")
        }

        // Existing implementation
    }

    @PostMapping("/{id}/sign")
    @PreAuthorize("hasAuthority('GENERATE_SIGNED_URL')")
    fun generateSignedUrl(
        @PathVariable id: Long,
        @RequestBody request: SignedUrlRequest
    ): ResponseEntity<SignedUrlResponse> {
        // Verify image exists
        imageService.getImageMetadata(id)

        val response = signedUrlService.generateSignedUrl(id, request.expiresIn)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE')")
    fun deleteImage(@PathVariable id: Long): ResponseEntity<Void> {
        // Existing implementation
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('SEARCH')")
    fun searchImages(...): ResponseEntity<List<ImageMetadataResponse>> {
        // Existing implementation
    }
}
```

**ApiKeyController (Admin):**
```kotlin
@RestController
@RequestMapping("/api/admin/keys")
class ApiKeyController(
    private val apiKeyService: ApiKeyService,
    private val apiKeyRepository: ApiKeyRepository
) {
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    fun createApiKey(@RequestBody request: CreateApiKeyRequest): ResponseEntity<CreateApiKeyResponse> {
        val (rawKey, apiKey) = apiKeyService.createApiKey(
            request.name,
            request.permissions
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            CreateApiKeyResponse(
                id = apiKey.id!!,
                name = apiKey.name,
                key = rawKey,  // Only shown once!
                permissions = apiKey.permissions,
                createdAt = apiKey.createdAt
            )
        )
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    fun listApiKeys(): ResponseEntity<List<ApiKeyInfo>> {
        val keys = apiKeyRepository.findAll().map { key ->
            ApiKeyInfo(
                id = key.id!!,
                name = key.name,
                permissions = key.permissions,
                createdAt = key.createdAt,
                lastUsedAt = key.lastUsedAt,
                active = key.active
            )
        }
        return ResponseEntity.ok(keys)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    fun revokeApiKey(@PathVariable id: Long): ResponseEntity<Void> {
        val apiKey = apiKeyRepository.findById(id)
            .orElseThrow { NoSuchElementException("API key not found") }

        apiKey.active = false
        apiKeyRepository.save(apiKey)

        return ResponseEntity.noContent().build()
    }
}
```

### 5. DTOs

```kotlin
data class SignedUrlRequest(
    val expiresIn: Long?  // Optional: seconds until expiration
)

data class SignedUrlResponse(
    val url: String,
    val expiresAt: Instant
)

data class CreateApiKeyRequest(
    val name: String,
    val permissions: Set<Permission>
)

data class CreateApiKeyResponse(
    val id: Long,
    val name: String,
    val key: String,  // Only shown once during creation!
    val permissions: Set<Permission>,
    val createdAt: LocalDateTime
)

data class ApiKeyInfo(
    val id: Long,
    val name: String,
    val permissions: Set<Permission>,
    val createdAt: LocalDateTime,
    val lastUsedAt: LocalDateTime?,
    val active: Boolean
)
```

### 6. Configuration

**application.yml:**
```yaml
imagestore:
  security:
    enabled: true  # Set to false for development
    secret-key: ${SIGNING_SECRET}  # From environment variable
    signed-url:
      default-expiry: 3600    # 1 hour
      max-expiry: 604800      # 7 days (maximum allowed)
    initial-admin-key:
      enabled: true  # Generate admin key on first startup
      name: "Initial Admin Key"
```

### 7. Dependencies

**build.gradle:**
```gradle
dependencies {
    // Add Spring Security
    implementation 'org.springframework.boot:spring-boot-starter-security'

    // BCrypt for API key hashing (included in Spring Security)
    // HMAC-SHA256 for signed URLs (included in JDK)

    testImplementation 'org.springframework.security:spring-security-test'
}
```

## Access Control Matrix

| Operation         | Public | Signed URL | API Key (UPLOAD) | API Key (DELETE) | API Key (ADMIN) |
|-------------------|--------|------------|------------------|------------------|-----------------|
| Upload Image      | ❌     | ❌         | ✅               | ✅               | ✅              |
| Get Image         | ❌     | ✅         | ✅               | ✅               | ✅              |
| Get Metadata      | ❌     | ✅         | ✅               | ✅               | ✅              |
| Search Images     | ❌     | ❌         | ✅               | ✅               | ✅              |
| Delete Image      | ❌     | ❌         | ❌               | ✅               | ✅              |
| Generate Signed URL| ❌    | ❌         | ✅               | ✅               | ✅              |
| Create API Key    | ❌     | ❌         | ❌               | ❌               | ✅              |
| List API Keys     | ❌     | ❌         | ❌               | ❌               | ✅              |
| Revoke API Key    | ❌     | ❌         | ❌               | ❌               | ✅              |

## Usage Examples

### 1. Initial Setup (Generate Admin Key)

On first startup with `imagestore.security.initial-admin-key.enabled=true`, the system logs:
```
========================================
INITIAL ADMIN API KEY (save this!)
========================================
sk_live_abc123def456...xyz
========================================
```

### 2. Create API Key for Application

```bash
curl -X POST http://localhost:8080/api/admin/keys \
  -H "Authorization: Bearer sk_live_admin_key_here" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Mobile App",
    "permissions": ["UPLOAD", "GENERATE_SIGNED_URL"]
  }'

Response:
{
  "id": 2,
  "name": "Mobile App",
  "key": "sk_live_mobile_abc123...",  // Save this! Won't be shown again
  "permissions": ["UPLOAD", "GENERATE_SIGNED_URL"],
  "createdAt": "2025-10-04T14:00:00"
}
```

### 3. Upload Image with API Key

```bash
curl -X POST http://localhost:8080/api/images \
  -H "Authorization: Bearer sk_live_mobile_abc123..." \
  -F "file=@image.jpg" \
  -F "tags=nature,landscape"

Response:
{
  "id": 1,
  "filename": "image.jpg",
  "contentType": "image/jpeg",
  "size": 245678,
  "uploadDate": "2025-10-04T14:01:00",
  "tags": ["nature", "landscape"]
}
```

### 4. Generate Signed URL

```bash
curl -X POST http://localhost:8080/api/images/1/sign \
  -H "Authorization: Bearer sk_live_mobile_abc123..." \
  -H "Content-Type: application/json" \
  -d '{"expiresIn": 3600}'

Response:
{
  "url": "/api/images/1?signature=hmac_sha256_xyz&expires=1733326800",
  "expiresAt": "2025-10-04T15:01:00Z"
}
```

### 5. Access Image with Signed URL (No Auth Needed)

```bash
curl "http://localhost:8080/api/images/1?signature=hmac_sha256_xyz&expires=1733326800" \
  -o downloaded.jpg

# Works until expiration time
# No Authorization header required
```

### 6. Access Image with API Key (Alternative)

```bash
curl http://localhost:8080/api/images/1 \
  -H "Authorization: Bearer sk_live_mobile_abc123..." \
  -o downloaded.jpg

# Works as long as API key is valid
```

### 7. Search Images

```bash
curl "http://localhost:8080/api/images/search?required=nature" \
  -H "Authorization: Bearer sk_live_mobile_abc123..."
```

## Migration Strategy

### Phase 1: Add Auth Components (Backward Compatible)
- Add all new entities, services, filters
- Set `imagestore.security.enabled: false` by default
- All existing tests pass without changes
- Document new features

### Phase 2: Enable Auth in Tests
- Add test configuration with security enabled
- Create test API keys
- Update test requests to include Authorization headers
- Ensure 100% test coverage maintained

### Phase 3: Enable Auth by Default
- Change default to `imagestore.security.enabled: true`
- Generate initial admin key on first startup
- Update README with auth examples
- Add migration guide for existing users

### Phase 4: Add Signed URLs
- Implement signed URL generation endpoint
- Add signature validation in image retrieval
- Maintain backward compatibility with authenticated requests
- Add signed URL examples to documentation

## Security Considerations

### Strengths
- ✅ Simple bearer token authentication (easy to implement/use)
- ✅ Permission-based access control (fine-grained authorization)
- ✅ Signed URLs prevent enumeration attacks
- ✅ Time-limited access (automatic expiration)
- ✅ HMAC-SHA256 signatures (industry standard, secure)
- ✅ BCrypt for API key hashing (slow, resistant to brute force)
- ✅ Stateless design (horizontally scalable)
- ✅ Constant-time signature comparison (prevents timing attacks)

### Limitations
- ⚠️ API keys are bearer tokens (must use HTTPS in production)
- ⚠️ No rate limiting (should add in production)
- ⚠️ No IP whitelisting (consider for admin endpoints)
- ⚠️ Signed URLs can be shared (not tied to specific user)
- ⚠️ No audit log (consider adding for compliance)
- ⚠️ Single secret key (no key rotation mechanism yet)

### Best Practices
1. **Always use HTTPS in production** - API keys transmitted in clear text
2. **Store API keys securely** - Environment variables, secret managers
3. **Rotate API keys periodically** - Especially admin keys
4. **Use short expiration times** - For signed URLs (1 hour default)
5. **Monitor last used timestamps** - Detect unused/compromised keys
6. **Rate limit endpoints** - Prevent abuse
7. **Log authentication failures** - Detect brute force attempts
8. **Keep secret key secure** - Used for HMAC signatures
9. **Use different keys per service** - Limit blast radius
10. **Revoke compromised keys immediately** - Delete endpoint available

### Production Checklist
- [ ] Generate strong secret key (32+ bytes)
- [ ] Store secret key in secure location (AWS Secrets Manager, etc.)
- [ ] Enable HTTPS/TLS
- [ ] Add rate limiting middleware
- [ ] Set up monitoring/alerting for auth failures
- [ ] Document API key management procedures
- [ ] Implement key rotation schedule
- [ ] Add audit logging
- [ ] Review and minimize permissions
- [ ] Test signed URL expiration behavior

## Testing Strategy

### Unit Tests
- `ApiKeyServiceTest`: Key creation, validation, permission checking
- `SignedUrlValidatorTest`: Signature generation/validation, expiration
- `SignedUrlServiceTest`: URL generation, request validation

### Integration Tests
- `ApiKeyAuthenticationTest`: Filter behavior, authentication flow
- `ProtectedEndpointsTest`: Access control for all endpoints
- `SignedUrlIntegrationTest`: End-to-end signed URL workflow
- `AdminApiKeyControllerTest`: Key management operations

### Security Tests
- Invalid API key rejection
- Expired signed URL rejection
- Tampered signature detection
- Permission enforcement
- Timing attack resistance

### Test Data
```kotlin
// Test API keys
const val ADMIN_KEY = "sk_test_admin_123"
const val UPLOAD_KEY = "sk_test_upload_456"
const val DELETE_KEY = "sk_test_delete_789"

// Test secret for signed URLs
const val TEST_SECRET = "test-secret-key-32-bytes-long-"
```

## Implementation Complexity

### Effort Estimate
- **Security Infrastructure** (filters, config): ~3 hours
- **API Key System** (entities, services, repos): ~2 hours
- **Signed URL System** (validation, generation): ~2 hours
- **Controller Updates** (auth integration): ~2 hours
- **Admin Endpoints** (key management): ~1 hour
- **Tests** (unit + integration): ~3 hours
- **Documentation** (README, OpenAPI): ~1 hour

**Total: ~14 hours**

### Files to Create
- `security/ApiKeyAuthenticationFilter.kt`
- `security/SignedUrlValidator.kt`
- `security/SecurityConfig.kt`
- `security/ApiKeyAuthentication.kt`
- `model/ApiKey.kt`
- `model/Permission.kt`
- `repository/ApiKeyRepository.kt`
- `service/ApiKeyService.kt`
- `service/SignedUrlService.kt`
- `controller/ApiKeyController.kt`
- `dto/SignedUrlRequest.kt`
- `dto/SignedUrlResponse.kt`
- `dto/CreateApiKeyRequest.kt`
- `dto/CreateApiKeyResponse.kt`
- `dto/ApiKeyInfo.kt`
- `config/InitialAdminKeyGenerator.kt`

### Files to Modify
- `controller/ImageController.kt` - Add auth checks, signed URL support
- `model/Image.kt` - Add uploadedByApiKeyId field
- `exception/GlobalExceptionHandler.kt` - Add auth exception handlers
- `build.gradle` - Add Spring Security dependency
- `application.yml` - Add security configuration
- All test files - Add auth headers

## Future Enhancements

### Short Term
- Rate limiting per API key
- API key usage statistics
- Audit log for all operations
- IP whitelisting for admin endpoints

### Medium Term
- Key rotation mechanism
- Scoped permissions (per-image, per-tag)
- Webhook notifications for auth events
- OAuth2/OIDC integration option

### Long Term
- Multi-tenancy support
- User accounts with social login
- Fine-grained ACLs (share image with specific users)
- API key expiration dates
