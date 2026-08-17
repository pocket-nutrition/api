package org.pocketnutrition.api.controller

import org.pocketnutrition.api.dto.IngredientSuggestion
import org.pocketnutrition.api.dto.RecipeComponent
import org.pocketnutrition.api.service.BarcodeService
import org.pocketnutrition.api.service.DensityLookupService
import org.pocketnutrition.api.service.IngredientSearchService
import org.pocketnutrition.api.service.NutritionService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(IngredientController::class)
class IngredientControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var ingredientSearchService: IngredientSearchService

    @MockitoBean
    private lateinit var nutritionService: NutritionService

    @MockitoBean
    private lateinit var barcodeService: BarcodeService

    @MockitoBean
    private lateinit var densityLookupService: DensityLookupService

    @Test
    fun `GET search returns suggestions`() {
        `when`(ingredientSearchService.search("chic", "fr", 10))
            .thenReturn(IngredientSearchService.SearchResult(1, listOf(IngredientSuggestion("poulet_filet", "Poulet, filet", "fr", portionG = 150.0))))

        mockMvc.get("/ingredients/search") {
            param("q", "chic")
            param("lang", "fr")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].ingredientId") { value("poulet_filet") }
            jsonPath("$[0].label") { value("Poulet, filet") }
            jsonPath("$[0].lang") { value("fr") }
            jsonPath("$[0].servingG") { value(150.0) }
            header { string("X-Segment-Count", "1") }
        }
    }

    @Test
    fun `GET search defaults portion and state for a suggestion the knowledge base does not cover`() {
        // The shape every Open Food Facts document has: index_search._build_off_document sets
        // typical_state and portion_measured_state to null, and portion_g is null whenever the
        // package quantity could not be parsed. Clients must not have to invent these, so the
        // response carries the server's defaults rather than nulls.
        `when`(ingredientSearchService.search("brocolis", "fr", 10))
            .thenReturn(
                IngredientSearchService.SearchResult(
                    1,
                    listOf(
                        IngredientSuggestion(
                            ingredientId = "off:0000000000000",
                            label = "Brocolis",
                            lang = "fr",
                            portionG = null,
                            typicalState = null,
                            portionMeasuredState = null,
                        ),
                    ),
                ),
            )

        mockMvc.get("/ingredients/search") {
            param("q", "brocolis")
            param("lang", "fr")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].servingG") { value(100.0) }
            jsonPath("$[0].typicalState") { value("raw") }
            jsonPath("$[0].portionMeasuredState") { value("cooked") }
        }
    }

    @Test
    fun `GET search forwards knowledge portion and state untouched when they exist`() {
        // The defaults must not mask real knowledge values. 214 g / raw is what the live API returns
        // for ingredient "spinach" (knowledge ingredients/spinach.yaml -> states.raw.portion_g).
        `when`(ingredientSearchService.search("epinard", "fr", 10))
            .thenReturn(
                IngredientSearchService.SearchResult(
                    1,
                    listOf(
                        IngredientSuggestion(
                            ingredientId = "spinach",
                            label = "épinard",
                            lang = "fr",
                            portionG = 214.0,
                            typicalState = "raw",
                            portionMeasuredState = "raw",
                        ),
                    ),
                ),
            )

        mockMvc.get("/ingredients/search") {
            param("q", "epinard")
            param("lang", "fr")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].servingG") { value(214.0) }
            jsonPath("$[0].portionMeasuredState") { value("raw") }
        }
    }

    @Test
    fun `GET search with blank q returns 400`() {
        mockMvc.get("/ingredients/search") {
            param("q", "   ")
        }.andExpect {
            status { isBadRequest() }
        }
    }

    @Test
    fun `GET search without lang param uses null`() {
        `when`(ingredientSearchService.search("riz", null, 10))
            .thenReturn(IngredientSearchService.SearchResult(1, listOf(IngredientSuggestion("riz_blanc", "Riz blanc", "fr"))))

        mockMvc.get("/ingredients/search") {
            param("q", "riz")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].ingredientId") { value("riz_blanc") }
        }
    }

    @Test
    fun `GET search caps limit at 50`() {
        `when`(ingredientSearchService.search("test", null, 50))
            .thenReturn(IngredientSearchService.SearchResult(1, emptyList()))

        mockMvc.get("/ingredients/search") {
            param("q", "test")
            param("limit", "999")
        }.andExpect {
            status { isOk() }
        }
    }

    @Test
    fun `GET search returns X-Segment-Count 2 for multi-term query`() {
        `when`(ingredientSearchService.search("riz carotte", null, 10))
            .thenReturn(IngredientSearchService.SearchResult(2, listOf(
                IngredientSuggestion("riz_blanc", "Riz blanc", "fr"),
                IngredientSuggestion("carotte", "Carotte", "fr"),
            )))

        mockMvc.get("/ingredients/search") {
            param("q", "riz carotte")
        }.andExpect {
            status { isOk() }
            header { string("X-Segment-Count", "2") }
        }
    }

    @Test
    fun `GET search maps recipe components into the response`() {
        val recipe = IngredientSuggestion(
            ingredientId = "recipe:raclette",
            label = "raclette traditionnelle",
            lang = "fr",
            productType = "recipe",
            recipeComponents = listOf(
                RecipeComponent(
                    weightG = 200.0,
                    cookingMethod = "boiled",
                    ingredient = IngredientSuggestion("potato", "pomme de terre", "fr", portionG = 80.0),
                ),
                RecipeComponent(
                    weightG = 100.0,
                    cookingMethod = "grilled",
                    ingredient = IngredientSuggestion("raclette_cheese", "raclette", "fr"),
                ),
            ),
        )
        `when`(ingredientSearchService.search("raclette", "fr", 10))
            .thenReturn(IngredientSearchService.SearchResult(1, listOf(recipe)))

        mockMvc.get("/ingredients/search") {
            param("q", "raclette")
            param("lang", "fr")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].ingredientId") { value("recipe:raclette") }
            jsonPath("$[0].productType") { value("recipe") }
            jsonPath("$[0].recipeComponents.length()") { value(2) }
            jsonPath("$[0].recipeComponents[0].weightG") { value(200.0) }
            jsonPath("$[0].recipeComponents[0].cookingMethod") { value("boiled") }
            jsonPath("$[0].recipeComponents[0].ingredient.ingredientId") { value("potato") }
            jsonPath("$[0].recipeComponents[0].ingredient.servingG") { value(80.0) }
            jsonPath("$[0].recipeComponents[1].ingredient.ingredientId") { value("raclette_cheese") }
        }
    }

    @Test
    fun `GET search with quantity does not request nutrition for recipes`() {
        val recipe = IngredientSuggestion(
            ingredientId = "recipe:raclette",
            label = "raclette traditionnelle",
            lang = "fr",
            productType = "recipe",
            recipeComponents = listOf(
                RecipeComponent(100.0, "grilled", IngredientSuggestion("raclette_cheese", "raclette", "fr")),
            ),
        )
        `when`(ingredientSearchService.search("raclette", null, 10))
            .thenReturn(IngredientSearchService.SearchResult(1, listOf(recipe)))
        `when`(nutritionService.getNutritionForResolved(emptyList())).thenReturn(emptyList())

        mockMvc.get("/ingredients/search") {
            param("q", "raclette")
            param("quantity", "100")
            param("cooking_method", "grilled")
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].nutrition") { doesNotExist() }
            jsonPath("$[0].recipeComponents.length()") { value(1) }
        }
    }

    @Test
    fun `GET barcode returns 404 for unknown code`() {
        `when`(barcodeService.findByBarcode("0000000000000")).thenReturn(null)

        mockMvc.get("/ingredients/barcode/0000000000000").andExpect {
            status { isNotFound() }
        }
    }
}
