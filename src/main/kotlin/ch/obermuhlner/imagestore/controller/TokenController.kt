package ch.obermuhlner.imagestore.controller

import ch.obermuhlner.imagestore.dto.TokenInfo
import ch.obermuhlner.imagestore.dto.TokenResponse
import ch.obermuhlner.imagestore.service.ImageAccessTokenService
import ch.obermuhlner.imagestore.service.ImageService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
@Tag(name = "Access Tokens", description = "Permanent access token management")
class TokenController(
    private val tokenService: ImageAccessTokenService,
    private val imageService: ImageService
) {

    @PostMapping("/images/{id}/token")
    @Operation(
        summary = "Generate access token",
        description = "Generate a permanent access token for an image. Token never expires but can be revoked. Requires GENERATE_SIGNED_URL permission."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "201", description = "Token generated successfully"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions"),
        ApiResponse(responseCode = "404", description = "Image not found")
    ])
    @PreAuthorize("hasAuthority('GENERATE_SIGNED_URL')")
    fun generateToken(@Parameter(description = "Image ID") @PathVariable id: Long): ResponseEntity<TokenResponse> {
        // Verify image exists
        imageService.getImageMetadata(id)

        val accessToken = tokenService.generateToken(id)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            TokenResponse(
                token = accessToken.token,
                imageId = accessToken.imageId,
                createdAt = accessToken.createdAt,
                active = accessToken.active
            )
        )
    }

    @GetMapping("/admin/tokens")
    @Operation(
        summary = "List all access tokens",
        description = "List all active access tokens. Requires ADMIN permission."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Tokens retrieved successfully"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun listAllTokens(): ResponseEntity<List<TokenInfo>> {
        val tokens = tokenService.listAllTokens().map { token ->
            TokenInfo(
                id = token.id!!,
                token = token.token,
                imageId = token.imageId,
                createdAt = token.createdAt,
                active = token.active
            )
        }
        return ResponseEntity.ok(tokens)
    }

    @GetMapping("/admin/tokens/image/{imageId}")
    @Operation(
        summary = "List tokens for image",
        description = "List all active access tokens for a specific image. Requires ADMIN permission."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Tokens retrieved successfully"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun listTokensForImage(@Parameter(description = "Image ID") @PathVariable imageId: Long): ResponseEntity<List<TokenInfo>> {
        val tokens = tokenService.listTokensForImage(imageId).map { token ->
            TokenInfo(
                id = token.id!!,
                token = token.token,
                imageId = token.imageId,
                createdAt = token.createdAt,
                active = token.active
            )
        }
        return ResponseEntity.ok(tokens)
    }

    @DeleteMapping("/admin/tokens/{token}")
    @Operation(
        summary = "Revoke access token",
        description = "Revoke a specific access token. Requires ADMIN permission."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "204", description = "Token revoked successfully"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun revokeToken(@Parameter(description = "Access token") @PathVariable token: String): ResponseEntity<Void> {
        tokenService.revokeToken(token)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/admin/tokens/image/{imageId}")
    @Operation(
        summary = "Revoke all tokens for image",
        description = "Revoke all access tokens for a specific image. Requires ADMIN permission."
    )
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Tokens revoked successfully"),
        ApiResponse(responseCode = "403", description = "Insufficient permissions")
    ])
    @PreAuthorize("hasAuthority('ADMIN')")
    fun revokeAllTokensForImage(@Parameter(description = "Image ID") @PathVariable imageId: Long): ResponseEntity<Map<String, Int>> {
        val count = tokenService.revokeAllTokensForImage(imageId)
        return ResponseEntity.ok(mapOf("revokedCount" to count))
    }
}
