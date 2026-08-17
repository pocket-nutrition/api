package org.pocketnutrition.api.service

import org.pocketnutrition.api.client.dto.MlRawProfile
import org.springframework.stereotype.Service

@Service
class RawProfileLookupService(
    private val ingredientSearchService: IngredientSearchService,
) {
    fun lookup(ingredientId: String): MlRawProfile? =
        ingredientSearchService.lookupProfile(ingredientId)
            ?: ingredientSearchService.lookupProfile("off:$ingredientId")
}
