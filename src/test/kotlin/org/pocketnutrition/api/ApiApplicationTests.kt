package org.pocketnutrition.api

import org.pocketnutrition.api.repository.NutritionCacheRepository
import org.pocketnutrition.api.service.FeedbackService
import org.pocketnutrition.api.service.IngredientResolutionService
import org.pocketnutrition.api.service.IngredientSearchService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean

@SpringBootTest(
    properties = [
        // Exclude JPA auto-configuration — PostgreSQL is not available during CI tests.
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
    ]
)
class ApiApplicationTests {

    // Prevents Spring Data Elasticsearch from trying to connect during context load
    @MockitoBean
    private lateinit var nutritionCacheRepository: NutritionCacheRepository

    // Prevents ES-backed services from querying ES during context load
    @MockitoBean
    private lateinit var ingredientResolutionService: IngredientResolutionService

    @MockitoBean
    private lateinit var ingredientSearchService: IngredientSearchService

    // Depends on the JPA FeedbackRepository (JPA auto-config is excluded above), so mock it.
    @MockitoBean
    private lateinit var feedbackService: FeedbackService

    @Test
    fun contextLoads() {}
}
