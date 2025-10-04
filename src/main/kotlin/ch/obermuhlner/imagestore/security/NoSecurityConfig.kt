package ch.obermuhlner.imagestore.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Security configuration that disables all authentication when imagestore.security.enabled=false.
 * This prevents Spring Boot's default auto-configuration from activating Basic/Form authentication.
 */
@Configuration
@EnableWebSecurity
@EnableAutoConfiguration(exclude = [UserDetailsServiceAutoConfiguration::class])
@ConditionalOnProperty(
    name = ["imagestore.security.enabled"],
    havingValue = "false",
    matchIfMissing = true
)
class NoSecurityConfig {

    @Bean
    fun permitAllSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }

        return http.build()
    }
}
