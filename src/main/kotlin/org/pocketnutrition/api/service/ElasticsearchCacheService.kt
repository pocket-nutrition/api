package org.pocketnutrition.api.service

import org.pocketnutrition.api.model.CachedNutritionResult
import org.pocketnutrition.api.repository.NutritionCacheRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.data.elasticsearch.NoSuchIndexException
import org.springframework.stereotype.Service

@Service
@ConditionalOnProperty(name = ["nutrition.cache.enabled"], havingValue = "true", matchIfMissing = true)
class ElasticsearchCacheService(
    private val repository: NutritionCacheRepository
) : CacheService {

    private val log = LoggerFactory.getLogger(ElasticsearchCacheService::class.java)

    override fun find(ingredientId: String, cookingMethod: String, measuredState: String): CachedNutritionResult? {
        val id = CachedNutritionResult.cacheKey(ingredientId, cookingMethod, measuredState)
        return try {
            repository.findById(id).orElse(null).also { hit ->
                if (hit != null) log.debug("Cache hit id={}", id)
                else log.debug("Cache miss id={}", id)
            }
        } catch (e: NoSuchIndexException) {
            log.debug("Cache index absent, treating as miss id={}", id)
            null
        }
    }

    override fun store(result: CachedNutritionResult) {
        repository.save(result)
        log.debug("Cached result id={}", result.id)
    }
}
