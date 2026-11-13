package org.pocketnutrition.api.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "https://showcase.pocketnutrition.org",
                // The published site, and its preview build that still carries the docs under review.
                "https://pocketnutrition.org",
                "https://website-beta.pocketnutrition.org",
                "https://sandbox.pocketnutrition.org",
                // The vault web viewer reads GET /reference-intakes to show daily targets.
                "https://me.pocketnutrition.org",
                "http://localhost:5173",
                "http://localhost:4173",
            )
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600)
    }
}
