package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.FoodItemRequest
import org.pocketnutrition.api.dto.NutritionalProfileResponse
import org.pocketnutrition.api.service.NutritionService
import org.pocketnutrition.api.controller.HealthController
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@WebMvcTest(NutritionController::class, HealthController::class)
class NutritionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var nutritionService: NutritionService

    @Test
    fun `POST nutrition returns 200 with nutritional profiles`() {
        val requestJson = """[{"name":"chicken","quantity":150.0,"unit":"g","cookingMethod":"grilled","measuredState":"raw"}]"""
        val expected = listOf(
            NutritionalProfileResponse(
                name = "chicken", quantity = 150.0, unit = "g",
                cookingMethod = "grilled", measuredState = "raw",
                energyKcal = 165.0, proteinG = 31.0, fatG = 3.6,
                carbohydratesG = 0.0, fiberG = null, waterG = null, sugarsG = null,
                calciumMg = null, ironMg = null, magnesiumMg = null, sodiumMg = null,
                vitaminCMg = null, saturatedFatG = null, monounsaturatedFatG = null,
                polyunsaturatedFatG = null, cholesterolMg = null, potassiumMg = null,
                phosphorusMg = null, zincMg = null, vitaminAUg = null, vitaminDUg = null,
                vitaminB6Mg = null, vitaminB12Ug = null, niacinMg = null,
                confidence = 0.87, source = "ml"
            )
        )
        `when`(nutritionService.getNutrition(listOf(FoodItemRequest("chicken", 150.0, "g", "grilled", "raw"))))
            .thenReturn(expected)

        mockMvc.post("/nutrition") {
            contentType = MediaType.APPLICATION_JSON
            content = requestJson
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("chicken") }
            jsonPath("$[0].quantity") { value(150.0) }
            jsonPath("$[0].unit") { value("g") }
            jsonPath("$[0].measuredState") { value("raw") }
            jsonPath("$[0].energyKcal") { value(165.0) }
            jsonPath("$[0].confidence") { value(0.87) }
            jsonPath("$[0].source") { value("ml") }
        }
    }

    @Test
    fun `POST nutrition with empty list returns 400`() {
        mockMvc.post("/nutrition") {
            contentType = MediaType.APPLICATION_JSON
            content = "[]"
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `POST nutrition with unsupported unit returns 400`() {
        mockMvc.post("/nutrition") {
            contentType = MediaType.APPLICATION_JSON
            content = """[{"name":"chicken","quantity":150.0,"unit":"kg","cookingMethod":"grilled","measuredState":"raw"}]"""
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET health returns 200`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/health")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk
        )
    }
}
