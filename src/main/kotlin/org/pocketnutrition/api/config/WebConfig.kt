package org.pocketnutrition.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Browser origins allowed to call the API, as patterns (`pn.cors.allowed-origins`, env
 * `PN_CORS_ALLOWED_ORIGINS`, comma-separated). The default admits the published site, every
 * pocketnutrition.org subdomain and any local port; a self-hosted API sets its own domains.
 * Credentials are never allowed, so a wildcard subdomain cannot read anything a cookie protects.
 */
@Configuration
class WebConfig(
    @Value("\${pn.cors.allowed-origins:https://pocketnutrition.org,https://*.pocketnutrition.org,http://localhost:[*]}")
    private val allowedOrigins: List<String>,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns(*allowedOrigins.filter { it.isNotBlank() }.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)
    }
}
