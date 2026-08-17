package org.pocketnutrition.api.service

import org.pocketnutrition.api.client.dto.MlIngredientInput
import org.pocketnutrition.api.client.dto.MlMealInput
import org.pocketnutrition.api.client.dto.MlPredictionResponse
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

@Service
class HttpMlClientService(
    private val mlRestClient: RestClient,
    private val rawProfileLookupService: RawProfileLookupService,
) : MlClientService {

    private val log = LoggerFactory.getLogger(HttpMlClientService::class.java)
    private val responseType = object : ParameterizedTypeReference<MlPredictionResponse>() {}

    override fun predict(items: List<ResolvedFoodItem>): List<MlPredictionResponse> =
        items.mapNotNull { item -> predictOne(item) }

    private fun predictOne(item: ResolvedFoodItem): MlPredictionResponse? {
        val rawProfile = rawProfileLookupService.lookup(item.ingredientId)
        val meal = MlMealInput(
            cooking_type = item.request.cookingMethod,
            ingredients = listOf(
                MlIngredientInput(
                    ingredient_id = item.ingredientId,
                    weight_g = item.effectiveWeightG,
                    measured_state = item.request.measuredState,
                    raw_profile = rawProfile,
                )
            ),
        )
        return try {
            mlRestClient.post()
                .uri("/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .body(meal)
                .retrieve()
                .body(responseType)
        } catch (e: Exception) {
            log.error("ML service unavailable for '{}': {}", item.request.name ?: item.ingredientId, e.message)
            null
        }
    }
}
