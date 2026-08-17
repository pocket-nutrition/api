package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.FoodItemRequest
import org.pocketnutrition.api.dto.NutritionalProfileResponse
import org.pocketnutrition.api.service.NutritionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Nutrition", description = "Nutritional profile prediction")
@RestController
@RequestMapping("/nutrition")
class NutritionController(private val nutritionService: NutritionService) {

    @Operation(summary = "Predict nutritional profiles for a list of food items")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Nutritional profiles computed"),
        ApiResponse(responseCode = "400", description = "Empty list or unsupported unit")
    )
    @PostMapping
    fun getNutrition(
        @RequestBody items: List<FoodItemRequest>
    ): ResponseEntity<List<NutritionalProfileResponse>> {
        if (items.isEmpty()) return ResponseEntity.badRequest().build()
        if (items.any { it.unit !in setOf("g", "ml", "item") }) return ResponseEntity.badRequest().build()
        if (items.any { it.cookingMethod !in setOf("raw", "boiled", "steamed", "grilled", "roasted", "fried", "cooked") }) return ResponseEntity.badRequest().build()
        return ResponseEntity.ok(nutritionService.getNutrition(items))
    }
}
