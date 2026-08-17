package org.pocketnutrition.api.service

import org.pocketnutrition.api.model.CachedNutritionResult
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["nutrition.cache.enabled"], havingValue = "false")
class NoOpCacheService : CacheService {

    private val log = LoggerFactory.getLogger(NoOpCacheService::class.java)

    init {
        log.info("Nutrition cache disabled (nutrition.cache.enabled=false)")
    }

    override fun find(ingredientId: String, cookingMethod: String, measuredState: String): CachedNutritionResult? = null

    override fun store(result: CachedNutritionResult) = Unit
}
