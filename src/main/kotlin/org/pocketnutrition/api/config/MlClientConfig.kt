package org.pocketnutrition.api.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class MlClientConfig(
    @Value("\${ml.service.url}") private val mlServiceUrl: String
) {

    @Bean
    fun mlRestClient(): RestClient = RestClient.builder()
        .baseUrl(mlServiceUrl)
        .build()
}
