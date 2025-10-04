package ch.obermuhlner.imagestore.model

enum class Permission {
    UPLOAD,               // Upload new images
    DELETE,               // Delete images
    SEARCH,               // Search and list images
    GENERATE_SIGNED_URL,  // Create signed URLs for public access
    ADMIN                 // Administrative operations (manage API keys)
}
