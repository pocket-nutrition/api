package org.pocketnutrition.api.service

import org.pocketnutrition.api.client.dto.MlPredictionResponse

interface MlClientService {
    /**
     * Returns one [MlPredictionResponse] per item, in the same order as [items].
     * Returns an empty list if the ML service is unreachable.
     */
    fun predict(items: List<ResolvedFoodItem>): List<MlPredictionResponse>
}
