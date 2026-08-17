package org.pocketnutrition.api.service

import org.pocketnutrition.api.client.dto.MlNutritionalResult
import org.pocketnutrition.api.client.dto.MlPredictionResponse
import org.pocketnutrition.api.dto.FoodItemRequest
import org.pocketnutrition.api.model.CachedNutritionResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq

@ExtendWith(MockitoExtension::class)
class NutritionServiceTest {

    @Mock private lateinit var cacheService: CacheService
    @Mock private lateinit var mlClientService: MlClientService
    @Mock private lateinit var ingredientResolutionService: IngredientResolutionService
    @Mock private lateinit var densityLookupService: DensityLookupService
    @Mock private lateinit var ingredientSearchService: IngredientSearchService
    @InjectMocks private lateinit var nutritionService: NutritionService

    // Use 100 g so the per-100g scale factor is 1 and cached values equal response values.
    private val chickenRequest = FoodItemRequest("chicken", 100.0, "g", "grilled", "raw")

    private fun stubResolution(name: String, result: String? = null) {
        `when`(ingredientResolutionService.resolve(name)).thenReturn(result)
    }

    private fun mlResponse(
        energyKcal: Double = 165.0,
        proteinG: Double = 31.0,
        fatG: Double = 3.6,
        carbsG: Double = 0.0,
        fiberG: Double? = null,
        confidence: Double = 0.87,
        cookingType: String = "grilled",
    ) = MlPredictionResponse(
        cooking_type = cookingType,
        cooking_duration_min = null,
        ingredients = emptyList(),
        result = MlNutritionalResult(
            energy_kcal = energyKcal, protein_g = proteinG, fat_g = fatG,
            carbohydrates_g = carbsG, fiber_g = fiberG,
            water_g = null, sugars_g = null, calcium_mg = null, iron_mg = null,
            magnesium_mg = null, sodium_mg = null, vitamin_c_mg = null,
            saturated_fat_g = null, monounsaturated_fat_g = null, polyunsaturated_fat_g = null,
            cholesterol_mg = null, potassium_mg = null, phosphorus_mg = null, zinc_mg = null,
            vitamin_a_ug = null, vitamin_d_ug = null, vitamin_b6_mg = null, vitamin_b12_ug = null,
            niacin_mg = null, confidence = confidence,
        ),
    )

    private fun cachedChicken(energyKcal: Double = 165.0, source: String = "ml") = CachedNutritionResult(
        id = "chicken::grilled::raw", name = "chicken", cookingMethod = "grilled",
        energyKcal = energyKcal, proteinG = 31.0, fatG = 3.6,
        carbohydratesG = 0.0, fiberG = null, waterG = null, sugarsG = null,
        calciumMg = null, ironMg = null, magnesiumMg = null, sodiumMg = null,
        vitaminCMg = null, saturatedFatG = null, monounsaturatedFatG = null,
        polyunsaturatedFatG = null, cholesterolMg = null, potassiumMg = null,
        phosphorusMg = null, zincMg = null, vitaminAUg = null, vitaminDUg = null,
        vitaminB6Mg = null, vitaminB12Ug = null, niacinMg = null, confidence = 0.9,
        source = source,
    )

    @Test
    fun `returns cached result scaled to quantity without calling ML`() {
        stubResolution("chicken")
        `when`(cacheService.find("chicken", "grilled", "raw")).thenReturn(cachedChicken())

        val result = nutritionService.getNutrition(listOf(chickenRequest))

        assertEquals(1, result.size)
        assertEquals("cache", result[0].source)
        // per-100g × (100g / 100) = same value
        assertEquals(165.0, result[0].energyKcal)
        verifyNoInteractions(mlClientService)
    }

    @Test
    fun `cache miss goes directly to ML skipping OFF for non-raw cooking methods`() {
        stubResolution("chicken")
        `when`(cacheService.find("chicken", "grilled", "raw")).thenReturn(null)
        val resolvedChicken = ResolvedFoodItem(chickenRequest, "chicken")
        `when`(mlClientService.predict(listOf(resolvedChicken))).thenReturn(listOf(mlResponse()))

        val result = nutritionService.getNutrition(listOf(chickenRequest))

        assertEquals(1, result.size)
        assertEquals("ml", result[0].source)
        assertEquals(0.87, result[0].confidence)
        val captor = argumentCaptor<CachedNutritionResult>()
        verify(cacheService).store(captor.capture())
        // Stored value must be per-100g (scale=1 for 100g, so same as ML value)
        assertEquals(165.0, captor.firstValue.energyKcal, 0.001)
        assertEquals("ml", captor.firstValue.source)
    }

    @Test
    fun `resolution resolves name to canonical ingredient ID`() {
        stubResolution("chicken breast", "poulet_filet")
        val breastRequest = FoodItemRequest("chicken breast", 100.0, "g", "grilled", "raw")
        `when`(cacheService.find("poulet_filet", "grilled", "raw")).thenReturn(
            cachedChicken().copy(id = "poulet_filet::grilled::raw", name = "chicken breast")
        )

        val result = nutritionService.getNutrition(listOf(breastRequest))

        assertEquals(1, result.size)
        assertEquals("cache", result[0].source)
        assertEquals("chicken breast", result[0].name)
        verifyNoInteractions(mlClientService)
    }

    @Test
    fun `returns unavailable when cache miss and ML returns nothing`() {
        stubResolution("chicken")
        `when`(cacheService.find("chicken", "grilled", "raw")).thenReturn(null)
        val resolvedChicken = ResolvedFoodItem(chickenRequest, "chicken")
        `when`(mlClientService.predict(listOf(resolvedChicken))).thenReturn(emptyList())

        val result = nutritionService.getNutrition(listOf(chickenRequest))

        assertEquals(1, result.size)
        assertEquals("unavailable", result[0].source)
        assertEquals(0.0, result[0].confidence)
    }

    @Test
    fun `handles mixed cache hits and misses in correct order`() {
        val riceRequest = FoodItemRequest("rice", 100.0, "g", "boiled", "raw")
        stubResolution("chicken")
        stubResolution("rice")
        `when`(cacheService.find("chicken", "grilled", "raw")).thenReturn(cachedChicken())
        `when`(cacheService.find("rice", "boiled", "raw")).thenReturn(null)
        val resolvedRice = ResolvedFoodItem(riceRequest, "rice")
        val mlRice = mlResponse(energyKcal = 130.0, proteinG = 2.7, fatG = 0.3,
            carbsG = 28.0, fiberG = 0.4, confidence = 0.75, cookingType = "boiled")
        `when`(mlClientService.predict(listOf(resolvedRice))).thenReturn(listOf(mlRice))

        val result = nutritionService.getNutrition(listOf(chickenRequest, riceRequest))

        assertEquals(2, result.size)
        assertEquals("cache", result[0].source)
        assertEquals("ml", result[1].source)
        assertEquals(0.75, result[1].confidence)
        assertNotNull(result[1].fiberG)
    }

    @Test
    fun `ML result below confidence threshold marks available as false`() {
        stubResolution("chicken")
        `when`(cacheService.find("chicken", "grilled", "raw")).thenReturn(null)
        val resolvedChicken = ResolvedFoodItem(chickenRequest, "chicken")
        `when`(mlClientService.predict(listOf(resolvedChicken))).thenReturn(listOf(mlResponse(confidence = 0.55)))

        val result = nutritionService.getNutrition(listOf(chickenRequest))

        assertEquals(1, result.size)
        assertEquals("ml", result[0].source)
        assertEquals(false, result[0].available)
    }

    @Test
    fun `ML result with null result field maps to unavailable values`() {
        stubResolution("chicken")
        val nullResultResponse = MlPredictionResponse(
            cooking_type = "grilled", cooking_duration_min = null, ingredients = emptyList(), result = null,
        )
        `when`(cacheService.find("chicken", "grilled", "raw")).thenReturn(null)
        val resolvedChicken = ResolvedFoodItem(chickenRequest, "chicken")
        `when`(mlClientService.predict(listOf(resolvedChicken))).thenReturn(listOf(nullResultResponse))

        val result = nutritionService.getNutrition(listOf(chickenRequest))

        assertEquals(1, result.size)
        assertEquals(0.0, result[0].energyKcal)
        assertEquals(0.0, result[0].confidence)
    }

    @Test
    fun `cache hit propagates original source field`() {
        stubResolution("chicken")
        `when`(cacheService.find("chicken", "grilled", "raw")).thenReturn(cachedChicken(source = "off_direct"))

        val result = nutritionService.getNutrition(listOf(chickenRequest))

        assertEquals("off_direct", result[0].source)
        verifyNoInteractions(mlClientService)
    }

    @Test
    fun `item unit multiplies quantity by portion_g before ML call`() {
        val bananaRequest = FoodItemRequest("banana", 1.0, "item", "raw", "raw")
        stubResolution("banana")
        `when`(ingredientSearchService.lookupDocument("banana")).thenReturn(SearchDoc(portionG = 120.0))
        `when`(cacheService.find("banana", "raw", "raw")).thenReturn(null)
        // effectiveWeightG = 1 * 120 = 120g → same scale as 100g request
        val resolvedBanana = ResolvedFoodItem(bananaRequest, "banana", effectiveWeightG = 120.0)
        `when`(mlClientService.predict(listOf(resolvedBanana))).thenReturn(listOf(mlResponse(energyKcal = 89.0, proteinG = 1.1, fatG = 0.3, carbsG = 23.0, confidence = 0.9)))

        val result = nutritionService.getNutrition(listOf(bananaRequest))

        assertEquals(1, result.size)
        assertEquals("item", result[0].unit)
        assertEquals(1.0, result[0].quantity)
        assertEquals(89.0, result[0].energyKcal, 0.01)
    }

    @Test
    fun `item unit falls back to quantity when portion_g not found`() {
        val unknownRequest = FoodItemRequest("unknown_fruit", 2.0, "item", "raw", "raw")
        stubResolution("unknown_fruit")
        `when`(ingredientSearchService.lookupDocument("unknown_fruit")).thenReturn(null)
        `when`(cacheService.find("unknown_fruit", "raw", "raw")).thenReturn(null)
        val resolvedItem = ResolvedFoodItem(unknownRequest, "unknown_fruit", effectiveWeightG = 2.0)
        `when`(mlClientService.predict(listOf(resolvedItem))).thenReturn(emptyList())

        val result = nutritionService.getNutrition(listOf(unknownRequest))

        assertEquals(1, result.size)
        assertEquals("unavailable", result[0].source)
    }
}
