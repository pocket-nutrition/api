package org.pocketnutrition.api.repository

import org.pocketnutrition.api.model.CachedNutritionResult
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository

interface NutritionCacheRepository : ElasticsearchRepository<CachedNutritionResult, String>
