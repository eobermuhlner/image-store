package ch.obermuhlner.imagestore.security

import ch.obermuhlner.imagestore.model.ApiKey
import ch.obermuhlner.imagestore.model.Permission
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority

class ApiKeyAuthentication(
    val apiKey: ApiKey
) : Authentication {

    private var authenticated = true

    override fun getName(): String = apiKey.name

    override fun getAuthorities(): Collection<GrantedAuthority> {
        return apiKey.permissions.map { SimpleGrantedAuthority(it.name) }
    }

    override fun getCredentials(): Any = apiKey.keyHash

    override fun getDetails(): Any = apiKey

    override fun getPrincipal(): Any = apiKey

    override fun isAuthenticated(): Boolean = authenticated

    override fun setAuthenticated(isAuthenticated: Boolean) {
        this.authenticated = isAuthenticated
    }

    fun hasPermission(permission: Permission): Boolean {
        return permission in apiKey.permissions
    }
}
