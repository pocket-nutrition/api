package org.pocketnutrition.api.contract

import org.pocketnutrition.api.repository.NutritionCacheRepository
import org.pocketnutrition.api.service.FeedbackService
import org.pocketnutrition.api.service.IngredientResolutionService
import org.pocketnutrition.api.service.IngredientSearchService
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.databind.ObjectMapper

/**
 * Pins the published OpenAPI document to a committed snapshot at `contract/openapi.json`.
 *
 * Why this exists: the mobile clients (pocket-nutrition-android, pocket-nutrition-ios) generate
 * their HTTP layer from this document and ship it inside a binary that cannot be updated in
 * lockstep with the server. A field rename here is a one-line PR that permanently breaks every
 * installed app. This test makes any contract change *visible in review* as a diff rather than
 * incidental.
 *
 * When it fails because you changed the contract deliberately:
 *
 *     UPDATE_CONTRACT=1 ./mvnw test -Dtest=OpenApiSnapshotTest
 *
 * then review the diff in `contract/openapi.json`, bump `pn.contract.version` in
 * application.properties (minor = additive, major = breaking), and commit both.
 */
@SpringBootTest(
    properties = [
        // Mirrors ApiApplicationTests: PostgreSQL is not available during CI tests.
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
    ],
)
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    // Same context-load guards as ApiApplicationTests — no external service is reachable in CI.
    @MockitoBean
    private lateinit var nutritionCacheRepository: NutritionCacheRepository

    @MockitoBean
    private lateinit var ingredientResolutionService: IngredientResolutionService

    @MockitoBean
    private lateinit var ingredientSearchService: IngredientSearchService

    @MockitoBean
    private lateinit var feedbackService: FeedbackService

    @Test
    fun `committed contract snapshot matches the generated document`() {
        val live = fetchSpecPrettyPrinted()

        if (System.getenv("UPDATE_CONTRACT") == "1") {
            SNAPSHOT_FILE.parentFile?.mkdirs()
            SNAPSHOT_FILE.writeText(live + "\n")
            println("contract snapshot rewritten: ${SNAPSHOT_FILE.absolutePath}")
            return
        }

        assertTrue(
            SNAPSHOT_FILE.exists(),
            "Missing ${SNAPSHOT_FILE.path}. Generate it with: " +
                "UPDATE_CONTRACT=1 ./mvnw test -Dtest=OpenApiSnapshotTest",
        )

        assertEquals(
            SNAPSHOT_FILE.readText().trim(),
            live,
            "The OpenAPI document has drifted from the committed snapshot. If the change is " +
                "intended, run: UPDATE_CONTRACT=1 ./mvnw test -Dtest=OpenApiSnapshotTest, " +
                "review the diff, bump pn.contract.version, and commit both files.",
        )
    }

    /**
     * Records the two gaps the pocket-nutrition-contracts overlay compensates for. Plain drift
     * detection cannot catch them because they are *absences*, not wrong values.
     *
     * springdoc emits no `required` arrays and does not document response headers, so a naive
     * generator produces an all-optional model and no way to read X-Segment-Count. If springdoc
     * ever starts emitting them, this test fails and the overlay gets shrunk deliberately instead
     * of silently conflicting.
     */
    @Test
    fun `known gaps the contracts overlay compensates for are still present`() {
        val doc = ObjectMapper().readTree(fetchSpecPrettyPrinted())

        val profile = doc.path("components").path("schemas").path("NutritionalProfileResponse")
        assertTrue(profile.isObject, "NutritionalProfileResponse missing from the document")
        assertTrue(
            profile.path("required").isMissingNode,
            "springdoc now emits `required` for NutritionalProfileResponse. Shrink the `required` " +
                "section of pocket-nutrition-contracts/openapi/overlay.yaml accordingly.",
        )

        val searchHeaders = doc.path("paths").path("/ingredients/search").path("get")
            .path("responses").path("200").path("headers")
        assertTrue(
            searchHeaders.isMissingNode || searchHeaders.isEmpty,
            "GET /ingredients/search now documents response headers. Check whether " +
                "X-Segment-Count is covered and shrink the overlay accordingly.",
        )
    }

    @Test
    fun `published cookingMethod enum matches the controller validation whitelist`() {
        val doc = ObjectMapper().readTree(fetchSpecPrettyPrinted())
        val enumNode = doc.path("components").path("schemas").path("FoodItemRequest")
            .path("properties").path("cookingMethod").path("enum")

        val published = mutableSetOf<String>()
        enumNode.forEach { published.add(it.asString()) }

        // Mirrors the setOf(...) guard in NutritionController.getNutrition. These must agree, or a
        // generated client gets an enum that cannot express a value the server accepts — and that
        // is unfixable in an already-shipped binary.
        val accepted: Set<String> =
            setOf("raw", "boiled", "steamed", "grilled", "roasted", "fried", "cooked")

        assertEquals(
            accepted,
            published.toSet(),
            "The published cookingMethod enum disagrees with NutritionController's validation " +
                "whitelist.",
        )
    }

    @Test
    fun `document version matches the configured contract version`() {
        val doc = ObjectMapper().readTree(fetchSpecPrettyPrinted())
        assertEquals(
            EXPECTED_CONTRACT_VERSION,
            doc.path("info").path("version").asString(),
            "info.version must equal EXPECTED_CONTRACT_VERSION in this file, which must itself match "
                + "pn.contract.version in src/main/ and src/test/resources/application.properties",
        )
    }

    private fun fetchSpecPrettyPrinted(): String {
        val raw = mockMvc.get("/v3/api-docs")
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)

        val mapper = ObjectMapper()
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(raw))
    }

    private companion object {
        /** Maven runs tests with user.dir = the module basedir, so this resolves to the repo root. */
        val SNAPSHOT_FILE = File("contract/openapi.json")

        /**
         * The contract version, repeated here on purpose: a hardcoded literal is what makes a
         * version bump a deliberate edit rather than something that follows silently from a property
         * file. It must be kept in step with `pn.contract.version` in **both**
         * `src/main/resources/application.properties` and `src/test/resources/application.properties`
         * — three places in total.
         */
        const val EXPECTED_CONTRACT_VERSION = "1.3.0"
    }
}
