package org.pocketnutrition.api.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig(
    /**
     * Published contract version. Bump it in application.properties on every contract change:
     * minor for additive changes, major for breaking ones. Mobile clients pin a snapshot of this
     * document and cannot be updated in lockstep with the server, so this version is the only
     * coordinate they have for reasoning about compatibility.
     */
    @Value("\${pn.contract.version}") private val contractVersion: String,
) {

    @Bean
    fun openApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Pocket Nutrition API")
                .version(contractVersion)
                .description(
                    "Predicts cooked nutritional profiles from raw ingredients and cooking methods. " +
                    "Results are served from an Elasticsearch cache on repeat queries; " +
                    "cache misses are forwarded to the pocket-nutrition-ml prediction service."
                )
        )
}
