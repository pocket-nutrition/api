package org.pocketnutrition.api.controller

import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.pocketnutrition.api.dto.ReferenceIntakeValue
import org.pocketnutrition.api.dto.ReferenceIntakesResponse
import org.pocketnutrition.api.service.ReferenceIntakeService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@WebMvcTest(ReferenceIntakeController::class)
class ReferenceIntakeControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var referenceIntakeService: ReferenceIntakeService

    private val iron = ReferenceIntakeValue(
        nutrient = "Iron", nutrientKey = "ironMg", classification = "Minerals",
        population = "Adults", populationDescription = "Adults",
        ageFrom = 18.0, ageTo = 999.0, ageUnit = "y", ageLabel = "≥ 18 years", pal = null,
        sex = "Male", isMale = true, isFemale = false,
        valueKind = "pri", raw = "11", valueMin = 11.0, valueMax = 11.0, isIncrement = false,
        unit = "mg/day", sourceLink = "https://doi.org/10.2903/j.efsa.2015.4254", note = null,
    )

    @Test
    fun `GET reference-intakes returns the table with its source and a cache header`() {
        whenever(referenceIntakeService.referenceIntakes()).thenReturn(
            ReferenceIntakesResponse(source = ReferenceIntakeService.SOURCE, sourceUrl = ReferenceIntakeService.SOURCE_URL, values = listOf(iron))
        )

        mockMvc.get("/reference-intakes").andExpect {
            status { isOk() }
            header { string("Cache-Control", "max-age=86400, public") }
            jsonPath("$.source") { value(ReferenceIntakeService.SOURCE) }
            jsonPath("$.values[0].nutrient") { value("Iron") }
            jsonPath("$.values[0].nutrientKey") { value("ironMg") }
            jsonPath("$.values[0].valueKind") { value("pri") }
            jsonPath("$.values[0].valueMin") { value(11.0) }
        }
    }

    @Test
    fun `GET reference-intakes returns 503 while the table has not been loaded`() {
        whenever(referenceIntakeService.referenceIntakes()).thenReturn(
            ReferenceIntakesResponse(source = ReferenceIntakeService.SOURCE, sourceUrl = ReferenceIntakeService.SOURCE_URL, values = emptyList())
        )

        mockMvc.get("/reference-intakes").andExpect {
            status { isServiceUnavailable() }
        }
    }
}
